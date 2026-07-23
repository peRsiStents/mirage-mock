package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.miragemock.admin.dto.RunResult;
import com.miragemock.admin.mapper.TestCaseMapper;
import com.miragemock.admin.mapper.TestRunLogMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.TestCase;
import com.miragemock.common.entity.TestRunLog;
import com.miragemock.common.exception.BizException;
import com.miragemock.common.util.JsonUtils;
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
    private final RestTemplate restTemplate;

    @Autowired
    public TestCaseService(TestCaseMapper caseMapper, TestRunLogMapper logMapper, RestTemplate restTemplate) {
        this.caseMapper = caseMapper;
        this.logMapper = logMapper;
        this.restTemplate = restTemplate;
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

    public RunResult run(Long id) {
        return runProxy(get(id));
    }

    private RunResult runProxy(TestCase tc) {
        RunResult rr = new RunResult();
        rr.setAssertions(new ArrayList<>());

        String fullUrl;
        HttpHeaders httpHeaders;
        String body;
        HttpMethod method;
        try {
            fullUrl = buildUrl(tc);
            validateScheme(fullUrl);
            httpHeaders = buildHeaders(tc);
            body = useBody(tc);
            method = HttpMethod.valueOf(tc.getMethod().toUpperCase());
        } catch (BizException e) {
            rr.setError(e.getMessage());
            rr.setPassed(false);
            writeLog(tc, "proxy", rr);
            return rr;
        } catch (Exception e) {
            rr.setError("用例配置有误: " + rootMessage(e));
            rr.setPassed(false);
            writeLog(tc, "proxy", rr);
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
            Map<String, Object> r = evalAssertion(a,
                    rr.getHttpStatus() == null ? 0 : rr.getHttpStatus(), rr.getHeaders(), rr.getBody());
            rr.getAssertions().add(r);
            if (!Boolean.TRUE.equals(r.get("passed"))) {
                allPassed = false;
            }
        }
        rr.setPassed(allPassed && rr.getError() == null);
        writeLog(tc, "proxy", rr);
        return rr;
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

    private String buildUrl(TestCase tc) {
        String url = tc.getUrl() == null ? "" : tc.getUrl().trim();
        List<Map<String, Object>> params = parseList(tc.getQuery());
        if (params.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        String sep = url.contains("?") ? "&" : "?";
        for (Map<String, Object> p : params) {
            String k = str(p.get("k"));
            String v = str(p.get("v"));
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

    private HttpHeaders buildHeaders(TestCase tc) {
        HttpHeaders h = new HttpHeaders();
        for (Map<String, Object> p : parseList(tc.getHeaders())) {
            String k = str(p.get("k"));
            String v = str(p.get("v"));
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

    private String useBody(TestCase tc) {
        String bt = tc.getBodyType() == null ? "none" : tc.getBodyType().toLowerCase();
        if ("none".equals(bt)) {
            return null;
        }
        return tc.getBody();
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
