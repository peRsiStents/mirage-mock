package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.miragemock.admin.dto.CaseRunResult;
import com.miragemock.admin.dto.RunResult;
import com.miragemock.admin.mapper.TestCaseMapper;
import com.miragemock.admin.mapper.TestRunLogMapper;
import com.miragemock.admin.mapper.TestRunRecordMapper;
import com.miragemock.admin.mapper.TestEnvironmentMapper;
import com.miragemock.admin.mapper.TestVariableMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.TestCase;
import com.miragemock.common.entity.TestRunLog;
import com.miragemock.common.entity.TestRunRecord;
import com.miragemock.common.entity.TestEnvironment;
import com.miragemock.common.entity.TestVariable;
import com.miragemock.common.exception.BizException;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.dsl.eval.EvalContext;
import com.miragemock.dsl.eval.ExpressionEvaluator;
import com.miragemock.dsl.spi.SecretResolver;
import com.miragemock.dsl.spi.SeqProvider;
import com.miragemock.admin.security.TestTargetGuard;
import com.miragemock.admin.security.ProjectAuthz;
import com.miragemock.tcp.codec.MessageParser;
import com.miragemock.tcp.codec.MessageParserRegistry;
import com.miragemock.tcp.frame.FrameEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
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
    private final TestRunRecordMapper recordMapper;
    private final TestVariableMapper variableMapper;
    private final TestEnvironmentMapper environmentMapper;
    private final RestTemplate restTemplate;
    private final ExpressionEvaluator evaluator;
    private final SecretResolver secretResolver;
    private final SeqProvider seqProvider;
    private final MessageParserRegistry parserRegistry;
    private final TestTargetGuard targetGuard;
    private final ProjectAuthz authz;

    @Autowired
    public TestCaseService(TestCaseMapper caseMapper, TestRunLogMapper logMapper, TestRunRecordMapper recordMapper,
                           TestVariableMapper variableMapper, TestEnvironmentMapper environmentMapper,
                           RestTemplate restTemplate, ExpressionEvaluator evaluator,
                           SecretResolver secretResolver, SeqProvider seqProvider,
                           MessageParserRegistry parserRegistry, TestTargetGuard targetGuard,
                           ProjectAuthz authz) {
        this.caseMapper = caseMapper;
        this.logMapper = logMapper;
        this.recordMapper = recordMapper;
        this.variableMapper = variableMapper;
        this.environmentMapper = environmentMapper;
        this.restTemplate = restTemplate;
        this.evaluator = evaluator;
        this.secretResolver = secretResolver;
        this.seqProvider = seqProvider;
        this.parserRegistry = parserRegistry;
        this.targetGuard = targetGuard;
        this.authz = authz;
    }

    // ============ CRUD ============

    public List<TestCase> list(Long projectId) {
        // 列表瘦身：只取摘要列，排除 body/headers/query/assertions/dataSet/tcpConfig 等大字段；
        // 编辑/运行/导出经 get(id) 懒加载全量，避免大量用例(尤其含 base64 文件体)撑爆列表响应。
        return caseMapper.selectList(new LambdaQueryWrapper<TestCase>()
                .select(TestCase::getId, TestCase::getProjectId, TestCase::getName,
                        TestCase::getProtocol, TestCase::getMethod, TestCase::getUrl,
                        TestCase::getBodyType, TestCase::getMode, TestCase::getStatus,
                        TestCase::getTags, TestCase::getRemark,
                        TestCase::getCreateTime, TestCase::getUpdateTime)
                .eq(TestCase::getProjectId, projectId)
                .orderByDesc(TestCase::getCreateTime));
    }

    public TestCase get(Long id) {
        TestCase t = caseMapper.selectById(id);
        if (t == null) {
            throw new BizException(ResultCode.NOT_FOUND, "测试案例不存在");
        }
        authz.requireMember(t.getProjectId());
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
        if (patch.getBodyContentType() != null) exists.setBodyContentType(patch.getBodyContentType());
        if (patch.getAssertions() != null) exists.setAssertions(patch.getAssertions());
        if (patch.getMode() != null) exists.setMode(patch.getMode());
        if (patch.getStatus() != null) exists.setStatus(patch.getStatus());
        if (patch.getRemark() != null) exists.setRemark(patch.getRemark());
        if (patch.getProtocol() != null) exists.setProtocol(patch.getProtocol());
        if (patch.getTcpConfig() != null) exists.setTcpConfig(patch.getTcpConfig());
        if (patch.getTags() != null) exists.setTags(patch.getTags());
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

    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<TestCase> cases = caseMapper.selectBatchIds(ids);
        for (TestCase t : cases) {
            authz.requireMember(t.getProjectId());
        }
        logMapper.delete(new LambdaQueryWrapper<TestRunLog>().in(TestRunLog::getCaseId, ids));
        caseMapper.deleteBatchIds(ids);
    }

    private void normalize(TestCase t) {
        if (t.getStatus() == null) t.setStatus(Constants.STATUS_ENABLED);
        if (t.getProtocol() == null || t.getProtocol().isEmpty()) t.setProtocol("HTTP");
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

    /** 数据驱动运行：按 dataSet 行逐行跑，汇总并落 test_run_record(target_type=case) */
    public CaseRunResult runData(Long id, Long envId) {
        TestCase tc = get(id);
        CaseRunResult res = new CaseRunResult();
        res.setResults(new ArrayList<>());
        List<Map<String, Object>> rows = parseList(tc.getDataSet());
        if (rows.isEmpty()) {
            RunResult rr;
            try { rr = executeCase(tc, buildEvalContext(tc.getProjectId(), envId, null)); }
            catch (Exception e) { rr = errorResult(e); }
            writeLog(tc, "proxy", rr);
            res.getResults().add(rr);
            res.setTotal(1);
            res.setPassedCount(Boolean.TRUE.equals(rr.getPassed()) ? 1 : 0);
            res.setPassed(rr.getPassed());
            return res;
        }
        Map<String, Object> baseVars;
        try { baseVars = buildEvalContext(tc.getProjectId(), envId, null).getVariables(); }
        catch (Exception e) { baseVars = new HashMap<>(); }
        List<Map<String, Object>> detail = new ArrayList<>();
        int passedCount = 0;
        long cost = 0;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> rowVars = new HashMap<>(baseVars);
            for (Map.Entry<String, Object> e : rows.get(i).entrySet()) {
                if (e.getKey() != null && !e.getKey().isEmpty()) rowVars.put("var." + e.getKey(), e.getValue());
            }
            EvalContext ctx = new EvalContext(rowVars, secretResolver, seqProvider, tc.getProjectId());
            RunResult rr;
            try { rr = executeCase(tc, ctx); }
            catch (Exception e) { rr = errorResult(e); }
            res.getResults().add(rr);
            if (Boolean.TRUE.equals(rr.getPassed())) passedCount++;
            cost += rr.getCostMs() == null ? 0 : rr.getCostMs();
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("row", i + 1);
            d.put("vars", rows.get(i));
            d.put("passed", rr.getPassed());
            d.put("httpStatus", rr.getHttpStatus());
            d.put("costMs", rr.getCostMs());
            d.put("error", rr.getError());
            d.put("body", truncate(rr.getBody()));
            d.put("assertions", rr.getAssertions());
            detail.add(d);
        }
        res.setTotal(rows.size());
        res.setPassedCount(passedCount);
        res.setPassed(passedCount == rows.size());
        TestRunRecord rec = new TestRunRecord();
        rec.setProjectId(tc.getProjectId());
        rec.setTargetType("case");
        rec.setTargetId(id);
        rec.setEnvId(envId);
        rec.setPassed(res.getPassed() ? 1 : 0);
        rec.setTotalSteps(rows.size());
        rec.setPassedSteps(passedCount);
        rec.setFailedSteps(rows.size() - passedCount);
        rec.setCostMs(cost);
        rec.setDetail(JsonUtils.toJson(detail));
        recordMapper.insert(rec);
        res.setRecordId(rec.getId());
        return res;
    }

    private RunResult errorResult(Exception e) {
        RunResult rr = new RunResult();
        rr.setAssertions(new ArrayList<>());
        rr.setPassed(false);
        rr.setError(rootMessage(e));
        return rr;
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 10_000 ? s.substring(0, 10_000) + "...(截断)" : s;
    }

    /**
     * 执行单个用例：求值 URL/头/体 → RestTemplate 转发 → 断言求值，返回结果。
     * 不写 test_run_log，供单用例运行与场景步骤复用。
     */
    public RunResult executeCase(TestCase tc, EvalContext ctx) {
        RunResult rr = new RunResult();
        rr.setAssertions(new ArrayList<>());
        if ("TCP".equalsIgnoreCase(tc.getProtocol())) {
            executeTcp(tc, ctx, rr);
        } else {
            executeHttp(tc, ctx, rr);
        }
        evalAssertions(rr, tc, ctx);
        return rr;
    }

    /** HTTP 执行（RestTemplate 转发）。 */
    private void executeHttp(TestCase tc, EvalContext ctx, RunResult rr) {
        String fullUrl;
        HttpHeaders httpHeaders;
        RequestBody reqBody;
        HttpMethod method;
        try {
            fullUrl = buildUrl(tc, ctx);
            validateScheme(fullUrl);
            targetGuard.assertAllowed(new URI(fullUrl).getHost());
            httpHeaders = buildHeaders(tc, ctx);
            reqBody = buildRequestBody(tc, ctx);
            method = HttpMethod.valueOf(tc.getMethod().toUpperCase());
        } catch (BizException e) {
            rr.setError(e.getMessage());
            return;
        } catch (Exception e) {
            rr.setError("用例配置有误: " + rootMessage(e));
            return;
        }
        long t0 = System.currentTimeMillis();
        try {
            if (reqBody.contentType != null && !httpHeaders.containsKey("Content-Type")) {
                httpHeaders.setContentType(reqBody.contentType);
            }
            RequestEntity<Object> req = new RequestEntity<>(reqBody.body, httpHeaders, method, new URI(fullUrl));
            ResponseEntity<String> resp = restTemplate.exchange(req, String.class);
            rr.setHttpStatus(resp.getStatusCodeValue());
            rr.setHeaders(flatten(resp.getHeaders()));
            rr.setBody(resp.getBody());
        } catch (Exception e) {
            rr.setError("请求失败: " + rootMessage(e));
        }
        rr.setCostMs(System.currentTimeMillis() - t0);
    }

    /** TCP 执行：按 tcp_config 加帧发送、短连接读裸 body、按报文格式解析为字段 JSON 赋给 body。 */
    private void executeTcp(TestCase tc, EvalContext ctx, RunResult rr) {
        long t0 = System.currentTimeMillis();
        try {
            Map<String, Object> tcpCfg = parseTcpConfig(tc.getTcpConfig());
            String frameConfig = str(tcpCfg.get("frameConfig"));
            String messageFormat = str(tcpCfg.get("messageFormat"));
            if (messageFormat.isEmpty()) {
                messageFormat = "json";
            }
            Object fmtObj = tcpCfg.get("formatConfig");
            @SuppressWarnings("unchecked")
            Map<String, Object> formatConfig = fmtObj instanceof Map ? (Map<String, Object>) fmtObj : null;

            String target = eval(tc.getUrl() == null ? "" : tc.getUrl().trim(), ctx);
            int colon = target.lastIndexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("目标应为 host:port：" + target);
            }
            String host = target.substring(0, colon).trim();
            int port = Integer.parseInt(target.substring(colon + 1).trim());
            if (host.isEmpty()) {
                host = "localhost";
            }
            targetGuard.assertAllowed(host);

            Map<String, Object> fields = JsonUtils.parseMap(eval(tc.getBody() == null ? "{}" : tc.getBody(), ctx));
            if (fields == null) {
                fields = new LinkedHashMap<>();
            }
            MessageParser parser = parserRegistry.resolve(messageFormat);
            byte[] payload = parser.encode(fields, formatConfig);
            byte[] framed = FrameEncoder.encode(payload, frameConfig);
            byte[] resp = tcpRoundTrip(host, port, framed);
            // 响应去帧：剥离 length_field 长度头/尾部分隔符，避免帧头字节混进解析内容
            // （否则报文前面会多出帧头字节，如长度头 0x74 被当成正文里的 't'）
            byte[] respPayload = FrameEncoder.strip(resp, frameConfig);
            Map<String, Object> respFields = parser.parse(respPayload, formatConfig);
            rr.setBody(JsonUtils.toJson(respFields));
            rr.setHeaders(new HashMap<>());
        } catch (Exception e) {
            rr.setError("TCP 请求失败: " + rootMessage(e));
        }
        rr.setCostMs(System.currentTimeMillis() - t0);
    }

    /** 短连接：发完 shutdown 输出（触发 close_end/短连接回写），读响应至 EOF。 */
    private byte[] tcpRoundTrip(String host, int port, byte[] request) throws IOException {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(host, port), 5000);
            sock.setSoTimeout(10000);
            OutputStream out = sock.getOutputStream();
            out.write(request);
            out.flush();
            sock.shutdownOutput();
            return readUntilEnd(sock.getInputStream());
        }
    }

    private byte[] readUntilEnd(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        try {
            while ((n = in.read(tmp)) != -1) {
                buf.write(tmp, 0, n);
            }
        } catch (SocketTimeoutException e) {
            // 长连接场景：响应已到、对端未关连接，SO_TIMEOUT 视为读完成
        }
        return buf.toByteArray();
    }

    private void evalAssertions(RunResult rr, TestCase tc, EvalContext ctx) {
        boolean allPassed = true;
        for (Map<String, Object> a : parseList(tc.getAssertions())) {
            a.put("expected", eval(str(a.get("expected")), ctx));
            Map<String, Object> r = evalAssertion(a,
                    rr.getHttpStatus() == null ? 0 : rr.getHttpStatus(), rr.getHeaders(), rr.getBody(), rr.getCostMs());
            rr.getAssertions().add(r);
            if (!Boolean.TRUE.equals(r.get("passed"))) {
                allPassed = false;
            }
        }
        rr.setPassed(allPassed && rr.getError() == null);
    }

    /** tcp_config JSON → {frameConfig, messageFormat, formatConfig}。 */
    private Map<String, Object> parseTcpConfig(String tcpConfig) {
        if (tcpConfig == null || tcpConfig.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> m = JsonUtils.parseMap(tcpConfig);
        return m == null ? new LinkedHashMap<>() : m;
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
    private Map<String, Object> evalAssertion(Map<String, Object> a, int status, Map<String, String> headers, String body, Long costMs) {
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
                    String display = jsonValueStr(val);
                    actual = display;
                    switch (op) {
                        case "exists":
                            actual = val == null ? "(无)" : display;
                            passed = val != null;
                            break;
                        case "contains":
                            passed = display.contains(expected);
                            break;
                        case "ne":
                            passed = !jsonSmartEquals(val, display, expected);
                            break;
                        case "gt":
                        case "lt":
                        case "ge":
                        case "le":
                            passed = jsonCompareNum(val, expected, op);
                            break;
                        default: // eq
                            passed = jsonSmartEquals(val, display, expected);
                            break;
                    }
                    break;
                }
                case "latencyLt": {
                    long c = costMs == null ? 0L : costMs;
                    actual = c + "ms";
                    passed = c < parseLong(expected, Long.MAX_VALUE);
                    break;
                }
                case "sizeGt": {
                    int len = body == null ? 0 : body.length();
                    actual = len + " bytes";
                    passed = len > parseLong(expected, Long.MAX_VALUE);
                    break;
                }
                case "headerExists": {
                    boolean exists = headers != null && headers.containsKey(target == null ? "" : target.toLowerCase());
                    actual = exists ? "存在" : "不存在";
                    passed = exists;
                    break;
                }
                case "jsonSchema": {
                    String msg = validateJsonSchema(body, expected);
                    passed = (msg == null);
                    actual = passed ? "符合" : msg;
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

    /** jsonPath 取值转展示串：标量直转，对象/数组紧凑 JSON，null→""（便于 contains/eq 字符串比较）。 */
    private String jsonValueStr(Object val) {
        if (val == null) {
            return "";
        }
        if (val instanceof Number || val instanceof Boolean || val instanceof String) {
            return String.valueOf(val);
        }
        try {
            return JsonUtils.toJson(val);
        } catch (Exception e) {
            return String.valueOf(val);
        }
    }

    /** 相等：两边均可解析为数字时按数值比（5 == 5.0），否则按字符串比。 */
    private boolean jsonSmartEquals(Object val, String display, String expected) {
        Double a = toDouble(val);
        Double b = toDouble(expected);
        if (a != null && b != null) {
            return Math.abs(a - b) < 1e-9;
        }
        return display.equals(expected);
    }

    /** 数值比较 op：gt/lt/ge/le；任一非数字则判失败。 */
    private boolean jsonCompareNum(Object val, String expected, String op) {
        Double a = toDouble(val);
        Double b = toDouble(expected);
        if (a == null || b == null) {
            return false;
        }
        switch (op) {
            case "gt": return a > b;
            case "lt": return a < b;
            case "ge": return a >= b;
            case "le": return a <= b;
            default: return false;
        }
    }

    private Double toDouble(Object o) {
        try {
            if (o instanceof Number) {
                return ((Number) o).doubleValue();
            }
            String s = o == null ? "" : String.valueOf(o).trim();
            return s.isEmpty() ? null : Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** JSON Schema 校验；通过返回 null，失败返回错误信息 */
    private String validateJsonSchema(String body, String schema) {
        if (body == null || body.isEmpty()) {
            return "响应体为空";
        }
        if (schema == null || schema.trim().isEmpty()) {
            return "未提供 Schema";
        }
        try {
            com.networknt.schema.JsonSchemaFactory factory =
                    com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V4);
            com.networknt.schema.JsonSchema s = factory.getSchema(schema);
            com.fasterxml.jackson.databind.JsonNode node = JsonUtils.mapper().readTree(body);
            java.util.Set<com.networknt.schema.ValidationMessage> errors = s.validate(node);
            if (errors.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (com.networknt.schema.ValidationMessage vm : errors) {
                sb.append(vm.getMessage()).append("; ");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Schema 校验异常: " + rootMessage(e);
        }
    }

    private long parseLong(String s, long def) {
        try {
            return Long.parseLong(str(s));
        } catch (Exception e) {
            return def;
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
        // Content-Type 由 buildRequestBody 按请求体类型决定（用户已设则不覆盖）
        return h;
    }

    /** 构建请求体：按 bodyType 返回体对象(String/byte[]/MultiValueMap)与 Content-Type；body/contentType 均空=不发体。 */
    private RequestBody buildRequestBody(TestCase tc, EvalContext ctx) {
        String bt = tc.getBodyType() == null ? "none" : tc.getBodyType().toLowerCase();
        String raw = tc.getBody();
        RequestBody rb = new RequestBody();
        if ("none".equals(bt) || raw == null) {
            return rb;
        }
        switch (bt) {
            case "form-data": {
                LinkedMultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
                for (Map<String, Object> row : parseList(raw)) {
                    String k = eval(str(row.get("k")), ctx);
                    if (k.isEmpty()) {
                        continue;
                    }
                    if ("file".equals(str(row.get("type"))) && row.get("dataB64") != null) {
                        byte[] bytes = decodeBase64(str(row.get("dataB64")));
                        if (bytes != null) {
                            String fn = eval(str(row.get("fileName")), ctx);
                            map.add(k, fileResource(bytes, fn.isEmpty() ? "file" : fn));
                        }
                    } else {
                        map.add(k, eval(str(row.get("v")), ctx));
                    }
                }
                if (!map.isEmpty()) {
                    rb.body = map;
                    rb.contentType = MediaType.MULTIPART_FORM_DATA;
                }
                break;
            }
            case "x-www-form-urlencoded": {
                LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();
                for (Map<String, Object> row : parseList(raw)) {
                    String k = eval(str(row.get("k")), ctx);
                    if (k.isEmpty()) {
                        continue;
                    }
                    map.add(k, eval(str(row.get("v")), ctx));
                }
                if (!map.isEmpty()) {
                    rb.body = map;
                    rb.contentType = MediaType.APPLICATION_FORM_URLENCODED;
                }
                break;
            }
            case "binary": {
                byte[] bytes = decodeBinaryBody(raw);
                if (bytes != null) {
                    rb.body = bytes;
                    rb.contentType = parseMediaType(tc.getBodyContentType(), MediaType.APPLICATION_OCTET_STREAM);
                }
                break;
            }
            case "raw":
                rb.body = eval(raw, ctx);
                rb.contentType = parseMediaType(tc.getBodyContentType(), null);
                break;
            case "json":
                rb.body = eval(raw, ctx);
                rb.contentType = MediaType.APPLICATION_JSON;
                break;
            case "form":
                rb.body = eval(raw, ctx);
                rb.contentType = MediaType.APPLICATION_FORM_URLENCODED;
                break;
            default:
                rb.body = eval(raw, ctx);
                break;
        }
        return rb;
    }

    private static final class RequestBody {
        Object body;
        MediaType contentType;
    }

    private ByteArrayResource fileResource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private byte[] decodeBinaryBody(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("{")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = JsonUtils.fromJson(s, Map.class);
                return decodeBase64(str(m.get("dataB64")));
            } catch (Exception ignored) {
                return null;
            }
        }
        return decodeBase64(s);
    }

    private byte[] decodeBase64(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(s);
        } catch (Exception e) {
            return null;
        }
    }

    private MediaType parseMediaType(String s, MediaType def) {
        if (s == null || s.trim().isEmpty()) {
            return def;
        }
        try {
            return MediaType.parseMediaType(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    // ============ 变量/常量（项目级） + 求值 ============

    /**
     * 构建求值上下文：合并 项目变量 + 环境变量 + 运行时(提取)变量 到 var.*，
     * 优先级 提取 > 环境 > 项目；环境 baseUrl 注入 __base_url（供相对URL拼接）。
     */
    public EvalContext buildEvalContext(Long projectId, Long envId, Map<String, Object> extraVars) {
        Map<String, Object> vars = buildBaseVars(projectId, envId);
        if (extraVars != null) {
            vars.putAll(extraVars);
        }
        return new EvalContext(vars, secretResolver, seqProvider, projectId);
    }

    /**
     * 项目变量 + 环境变量(含 baseUrl) 一次性加载为 var.* 映射，供场景多步骤/多数据行复用，
     * 避免每步、每行重复查库（原 buildEvalContext 每次都查两轮）。
     */
    public Map<String, Object> buildBaseVars(Long projectId, Long envId) {
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
        return vars;
    }

    /** 用预置变量映射构造求值上下文（场景复用 buildBaseVars 结果，每步只 new 一份副本注入运行时变量）。 */
    public EvalContext newContext(Long projectId, Map<String, Object> vars) {
        return new EvalContext(vars == null ? new HashMap<>() : vars, secretResolver, seqProvider, projectId);
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
            if (l != null && !l.isEmpty() && k != null) {
                // 统一小写键：header / headerExists / extract 断言均按小写查找
                m.put(k.toLowerCase(), String.join(",", l));
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
