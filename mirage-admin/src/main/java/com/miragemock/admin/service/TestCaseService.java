package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.miragemock.admin.dto.RunResult;
import com.miragemock.admin.mapper.TestCaseMapper;
import com.miragemock.admin.mapper.TestRunLogMapper;
import com.miragemock.admin.mapper.TestEnvironmentMapper;
import com.miragemock.admin.mapper.TestVariableMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.TestCase;
import com.miragemock.common.entity.TestRunLog;
import com.miragemock.common.entity.TestEnvironment;
import com.miragemock.common.entity.TestVariable;
import com.miragemock.common.exception.BizException;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.dsl.eval.EvalContext;
import com.miragemock.dsl.eval.ExpressionEvaluator;
import com.miragemock.dsl.spi.SecretResolver;
import com.miragemock.dsl.spi.SeqProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试案例管理：CRUD + proxy 运行(RestTemplate 转发 + 断言求值 + 记录) + 运行历史。
 */
@Service
public class TestCaseService {

    private static final int HISTORY_LIMIT = 100;

    private final TestCaseMapper caseMapper;
    private final TestRunLogMapper logMapper;
    private final TestVariableMapper variableMapper;
    private final TestEnvironmentMapper environmentMapper;
    private final RestTemplate restTemplate;
    private final ExpressionEvaluator evaluator;
    private final SecretResolver secretResolver;
    private final SeqProvider seqProvider;

    @Autowired
    public TestCaseService(TestCaseMapper caseMapper, TestRunLogMapper logMapper, TestVariableMapper variableMapper,
                           TestEnvironmentMapper environmentMapper, RestTemplate restTemplate,
                           ExpressionEvaluator evaluator, SecretResolver secretResolver, SeqProvider seqProvider) {
        this.caseMapper = caseMapper;
        this.logMapper = logMapper;
        this.variableMapper = variableMapper;
        this.environmentMapper = environmentMapper;
        this.restTemplate = restTemplate;
        this.evaluator = evaluator;
        this.secretResolver = secretResolver;
        this.seqProvider = seqProvider;
    }

    // ============ CRUD ============

    public List<TestCase> list(Long projectId) {
        return caseMapper.selectList(new LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getProjectId, projectId)
                .orderByDesc(TestCase::getCreateTime));
    }

    public TestCase get(Long id) {
        TestCase t = caseMapper.selectById(id);
        if (t == null) {
            throw new BizException(ResultCode.NOT_FOUND, "测试案例不存在");
        }
        return t;
    }

    @Transactional
    public TestCase create(TestCase t) {
        if (t.getProjectId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "projectId 不能为空");
        }
        normalize(t);
        caseMapper.insert(t);
        return t;
    }

    @Transactional
    public TestCase update(Long id, TestCase patch) {
        TestCase exists = get(id);
        if (patch.getName() != null) exists.setName(patch.getName());
        if (patch.getMethod() != null) exists.setMethod(patch.getMethod());
        if (patch.getUrl() != null) exists.setUrl(patch.getUrl());
        if (patch.getHeaders() != null) exists.setHeaders(patch.getHeaders());
        if (patch.getQuery() != null) exists.setQuery(patch.getQuery());
        if (patch.getBodyType() != null) exists.setBodyType(patch.getBodyType());
        if (patch.getBody() != null) exists.setBody(patch.getBody());
        if (patch.getAssertions() != null) exists.setAssertions(patch.getAssertions());
        if (patch.getMode() != null) exists.setMode(patch.getMode());
        if (patch.getStatus() != null) exists.setStatus(patch.getStatus());
        if (patch.getRemark() != null) exists.setRemark(patch.getRemark());
        normalize(exists);
        caseMapper.updateById(exists);
        return exists;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        logMapper.delete(new LambdaQueryWrapper<TestRunLog>().eq(TestRunLog::getCaseId, id));
        caseMapper.deleteById(id);
    }

    private void normalize(TestCase t) {
        if (t.getStatus() == null) t.setStatus(Constants.STATUS_ENABLED);
        if (t.getMethod() == null || t.getMethod().isEmpty()) t.setMethod("GET");
        if (t.getBodyType() == null || t.getBodyType().isEmpty()) t.setBodyType("none");
        if (t.getMode() == null || t.getMode().isEmpty()) t.setMode("proxy");
    }

    // ============ 运行（proxy） ============

    public RunResult run(Long id, Long envId) {
        TestCase tc = get(id);
        RunResult rr;
        try {
            rr = executeCase(tc, buildEvalContext(tc.getProjectId(), envId, null));
        } catch (Exception e) {
            rr = new RunResult();
            rr.setAssertions(new ArrayList<>());
            rr.setError("上下文初始化失败: " + rootMessage(e));
            rr.setPassed(false);
        }
        writeLog(tc, "proxy", rr);
        return rr;
    }

    /**
     * 执行单个用例：求值 URL/头/体 → RestTemplate 转发 → 断言求值，返回结果。
     * 不写 test_run_log，供单用例运行与场景步骤复用。
     */
    public RunResult executeCase(TestCase tc, EvalContext ctx) {
        RunResult rr = new RunResult();
        rr.setAssertions(new ArrayList<>());

        String fullUrl;
        HttpHeaders httpHeaders;
        String body;
        HttpMethod method;
        try {
            fullUrl = buildUrl(tc, ctx);
            validateScheme(fullUrl);
            httpHeaders = buildHeaders(tc, ctx);
            body = useBody(tc, ctx);
            method = HttpMethod.valueOf(tc.getMethod().toUpperCase());
        } catch (BizException e) {
            rr.setError(e.getMessage());
            rr.setPassed(false);
            return rr;
        } catch (Exception e) {
            rr.setError("用例配置有误: " + rootMessage(e));
            rr.setPassed(false);
            return rr;
        }

        long t0 = System.currentTimeMillis();
        try {
            RequestEntity<String> req = new RequestEntity<>(body, httpHeaders, method, new URI(fullUrl));
            ResponseEntity<String> resp = restTemplate.exchange(req, String.class);
            rr.setHttpStatus(resp.getStatusCodeValue());
            rr.setHeaders(flatten(resp.getHeaders()));
            rr.setBody(resp.getBody());
        } catch (Exception e) {
            rr.setError("请求失败: " + rootMessage(e));
        }
        rr.setCostMs(System.currentTimeMillis() - t0);

        // 断言求值
        boolean allPassed = true;
        for (Map<String, Object> a : parseList(tc.getAssertions())) {
            a.put("expected", eval(str(a.get("expected")), ctx));
            Map<String, Object> r = evalAssertion(a,
                    rr.getHttpStatus() == null ? 0 : rr.getHttpStatus(), rr.getHeaders(), rr.getBody());
            rr.getAssertions().add(r);
            if (!Boolean.TRUE.equals(r.get("passed"))) {
                allPassed = false;
            }
        }
        rr.setPassed(allPassed && rr.getError() == null);
        return rr;
    }

    /** 从响应提取变量（供场景步骤间传递）。返回 Map 键为 var.<name> */
    public Map<String, Object> extract(RunResult rr, List<Map<String, Object>> extractors) {
        Map<String, Object> out = new HashMap<>();
        if (extractors == null || rr == null) {
            return out;
        }
        for (Map<String, Object> e : extractors) {
            String varName = str(e.get("var"));
            String source = str(e.get("source"));
            String expr = str(e.get("expr"));
            if (varName.isEmpty()) {
                continue;
            }
            Object value = "";
            try {
                switch (source) {
                    case "status":
                        value = rr.getHttpStatus() == null ? "" : String.valueOf(rr.getHttpStatus());
                        break;
                    case "header":
                        Map<String, String> hs = rr.getHeaders();
                        value = hs == null ? "" : hs.get(expr.toLowerCase());
                        break;
                    case "body":
                        value = rr.getBody() == null ? "" : rr.getBody();
                        break;
                    case "jsonPath":
                        value = readJsonPath(rr.getBody(), expr);
                        break;
                    default:
                        continue;
                }
            } catch (Exception ex) {
                value = "";
            }
            out.put("var." + varName, value);
        }
        return out;
    }

    public List<TestRunLog> runs(Long caseId) {
        return logMapper.selectList(new LambdaQueryWrapper<TestRunLog>()
                .eq(TestRunLog::getCaseId, caseId)
                .orderByDesc(TestRunLog::getCreateTime)
                .last("LIMIT " + HISTORY_LIMIT));
    }

    // ============ 断言求值 ============

    @SuppressWarnings("unchecked")
    private Map<String, Object> evalAssertion(Map<String, Object> a, int status, Map<String, String> headers, String body) {
        String type = str(a.get("type"));
        String target = str(a.get("target"));
        String op = str(a.get("op"));
        if (op.isEmpty()) op = "eq";
        String expected = str(a.get("expected"));
        String actual = "";
        boolean passed = false;
        String message = null;
        try {
            switch (type) {
                case "status":
                    actual = String.valueOf(status);
                    passed = actual.equals(expected);
                    break;
                case "bodyContains":
                    actual = body == null ? "" : body;
                    passed = actual.contains(expected);
                    break;
                case "header": {
                    String hv = headers == null ? null : headers.get(target == null ? "" : target.toLowerCase());
                    actual = hv == null ? "" : hv;
                    passed = "contains".equals(op) ? actual.contains(expected) : actual.equals(expected);
                    break;
                }
                case "jsonPath": {
                    Object val = readJsonPath(body, target);
                    if ("exists".equals(op)) {
                        actual = val == null ? "(无)" : str(val);
                        passed = val != null;
                    } else {
                        actual = val == null ? "" : str(val);
                        passed = "contains".equals(op) ? actual.contains(expected) : actual.equals(expected);
                    }
                    break;
                }
                default:
                    message = "未知断言类型: " + type;
            }
        } catch (Exception e) {
            message = "断言异常: " + rootMessage(e);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("type", type);
        r.put("target", target);
        r.put("op", op);
        r.put("expected", expected);
        r.put("actual", actual);
        r.put("passed", passed);
        if (message != null) {
            r.put("message", message);
        }
        return r;
    }

    private Object readJsonPath(String body, String path) {
        if (body == null || body.isEmpty() || path == null || path.isEmpty()) {
            return null;
        }
        try {
            return JsonPath.read(body, path);
        } catch (Exception e) {
            return null;
        }
    }

    // ============ 请求构建 ============

    private String buildUrl(TestCase tc, EvalContext ctx) {
        String url = eval(tc.getUrl() == null ? "" : tc.getUrl().trim(), ctx);
        // 相对路径拼接环境 baseUrl（绝对 http(s):// 原样）
        if (!url.isEmpty() && !url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
            Object base = ctx.getVariable("__base_url");
            if (base != null && !base.toString().isEmpty()) {
                String b = base.toString();
                boolean bSlash = b.endsWith("/");
                boolean uSlash = url.startsWith("/");
                url = b + (bSlash && uSlash ? url.substring(1) : (!bSlash && !uSlash ? "/" + url : url));
            }
        }
        List<Map<String, Object>> params = parseList(tc.getQuery());
        if (params.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        String sep = url.contains("?") ? "&" : "?";
        for (Map<String, Object> p : params) {
            String k = str(p.get("k"));
            String v = eval(str(p.get("v")), ctx);
            if (k.isEmpty()) {
                continue;
            }
            try {
                sb.append(sep).append(URLEncoder.encode(k, "UTF-8")).append('=').append(URLEncoder.encode(v, "UTF-8"));
            } catch (Exception e) {
                sb.append(sep).append(k).append('=').append(v);
            }
            sep = "&";
        }
        return sb.toString();
    }

    private HttpHeaders buildHeaders(TestCase tc, EvalContext ctx) {
        HttpHeaders h = new HttpHeaders();
        for (Map<String, Object> p : parseList(tc.getHeaders())) {
            String k = str(p.get("k"));
            String v = eval(str(p.get("v")), ctx);
            if (!k.isEmpty()) {
                h.add(k, v);
            }
        }
        String bt = tc.getBodyType() == null ? "none" : tc.getBodyType().toLowerCase();
        if (!"none".equals(bt) && tc.getBody() != null && !tc.getBody().isEmpty() && !h.containsKey("Content-Type")) {
            if ("json".equals(bt)) {
                h.add("Content-Type", "application/json");
            } else if ("form".equals(bt)) {
                h.add("Content-Type", "application/x-www-form-urlencoded");
            }
        }
        return h;
    }

    private String useBody(TestCase tc, EvalContext ctx) {
        String bt = tc.getBodyType() == null ? "none" : tc.getBodyType().toLowerCase();
        if ("none".equals(bt)) {
            return null;
        }
        return eval(tc.getBody(), ctx);
    }

    // ============ 变量/常量（项目级） + 求值 ============

    /**
     * 构建求值上下文：合并 项目变量 + 环境变量 + 运行时(提取)变量 到 var.*，
     * 优先级 提取 > 环境 > 项目；环境 baseUrl 注入 __base_url（供相对URL拼接）。
     */
    public EvalContext buildEvalContext(Long projectId, Long envId, Map<String, Object> extraVars) {
        Map<String, Object> vars = new HashMap<>();
        for (TestVariable v : variableMapper.selectList(new LambdaQueryWrapper<TestVariable>()
                .eq(TestVariable::getProjectId, projectId))) {
            if (v.getName() != null && !v.getName().isEmpty()) {
                vars.put("var." + v.getName(), v.getVarValue());
            }
        }
        if (envId != null) {
            TestEnvironment env = environmentMapper.selectById(envId);
            if (env != null) {
                if (env.getBaseUrl() != null && !env.getBaseUrl().isEmpty()) {
                    vars.put("__base_url", env.getBaseUrl());
                }
                for (Map<String, Object> p : parseList(env.getVariables())) {
                    String n = str(p.get("name"));
                    if (!n.isEmpty()) {
                        vars.put("var." + n, p.get("value"));
                    }
                }
            }
        }
        if (extraVars != null) {
            vars.putAll(extraVars);
        }
        return new EvalContext(vars, secretResolver, seqProvider, projectId);
    }

    /** 求值含 ${...} 的字段（${var.x} / DSL 函数）；无 ${...} 原样返回 */
    private String eval(String s, EvalContext ctx) {
        if (s == null || s.isEmpty() || !s.contains("${")) {
            return s;
        }
        try {
            Object v = evaluator.evalTemplate(s, ctx);
            return v == null ? s : ExpressionEvaluator.stringify(v);
        } catch (Exception e) {
            throw new BizException(ResultCode.EXPRESSION_ERROR, "字段求值失败: " + rootMessage(e));
        }
    }

    public List<TestVariable> listVariables(Long projectId) {
        return variableMapper.selectList(new LambdaQueryWrapper<TestVariable>()
                .eq(TestVariable::getProjectId, projectId).orderByAsc(TestVariable::getName));
    }

    @Transactional
    public TestVariable createVariable(TestVariable v) {
        if (v.getProjectId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "projectId 不能为空");
        }
        if (v.getName() == null || v.getName().trim().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "变量名不能为空");
        }
        Long c = variableMapper.selectCount(new LambdaQueryWrapper<TestVariable>()
                .eq(TestVariable::getProjectId, v.getProjectId()).eq(TestVariable::getName, v.getName()));
        if (c != null && c > 0) {
            throw new BizException(ResultCode.CONFLICT, "变量名已存在: " + v.getName());
        }
        if (v.getStatus() == null) {
            v.setStatus(Constants.STATUS_ENABLED);
        }
        variableMapper.insert(v);
        return v;
    }

    @Transactional
    public TestVariable updateVariable(Long id, TestVariable patch) {
        TestVariable exists = variableMapper.selectById(id);
        if (exists == null) {
            throw new BizException(ResultCode.NOT_FOUND, "变量不存在");
        }
        if (patch.getVarValue() != null) exists.setVarValue(patch.getVarValue());
        if (patch.getRemark() != null) exists.setRemark(patch.getRemark());
        if (patch.getStatus() != null) exists.setStatus(patch.getStatus());
        variableMapper.updateById(exists);
        return exists;
    }

    @Transactional
    public void deleteVariable(Long id) {
        variableMapper.deleteById(id);
    }

    private void validateScheme(String url) {
        String scheme;
        try {
            scheme = new URI(url).getScheme();
        } catch (URISyntaxException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "URL 非法: " + e.getMessage());
        }
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅允许 http/https 地址");
        }
    }

    private Map<String, String> flatten(HttpHeaders h) {
        Map<String, String> m = new LinkedHashMap<>();
        if (h == null) {
            return m;
        }
        h.forEach((k, l) -> {
            if (l != null && !l.isEmpty()) {
                m.put(k, String.join(",", l));
            }
        });
        return m;
    }

    private void writeLog(TestCase tc, String mode, RunResult rr) {
        TestRunLog log = new TestRunLog();
        log.setProjectId(tc.getProjectId());
        log.setCaseId(tc.getId());
        log.setMode(mode);
        log.setHttpStatus(rr.getHttpStatus());
        log.setCostMs(rr.getCostMs());
        log.setPassed(Boolean.TRUE.equals(rr.getPassed()) ? Constants.STATUS_ENABLED : Constants.STATUS_DISABLED);
        log.setAssertionResult(JsonUtils.toJson(rr.getAssertions()));
        log.setError(rr.getError());
        logMapper.insert(log);
    }

    // ============ 工具 ============

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JsonUtils.fromJson(json, List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String rootMessage(Throwable e) {
        Throwable c = e;
        String m = e.getMessage();
        int i = 0;
        while (c.getCause() != null && i++ < 5) {
            c = c.getCause();
            m = c.getMessage();
        }
        return m == null ? e.getClass().getSimpleName() : m;
    }
}
