package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.miragemock.admin.dto.BatchRunResult;
import com.miragemock.admin.dto.CaseRunResult;
import com.miragemock.admin.dto.RunResult;
import com.miragemock.admin.mapper.TestCaseMapper;
import com.miragemock.admin.mapper.TestRunLogMapper;
import com.miragemock.admin.mapper.TestRunRecordMapper;
import com.miragemock.admin.mapper.TestScenarioStepMapper;
import com.miragemock.admin.mapper.TestEnvironmentMapper;
import com.miragemock.admin.mapper.TestVariableMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.TestCase;
import com.miragemock.common.entity.TestRunLog;
import com.miragemock.common.entity.TestRunRecord;
import com.miragemock.common.entity.TestScenarioStep;
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
import com.miragemock.tcp.frame.FrameReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 测试案例管理：CRUD + proxy 运行(RestTemplate 转发 + 断言求值 + 记录) + 运行历史。
 */
@Service
@Slf4j
public class TestCaseService {

    private static final int HISTORY_LIMIT = 100;

    /** TCP 用例读响应超时（毫秒）。按帧精确读取后一般不会触发。 */
    private static final int TCP_READ_TIMEOUT_MS = 10000;

    private final TestCaseMapper caseMapper;
    private final TestRunLogMapper logMapper;
    private final TestRunRecordMapper recordMapper;
    private final TestScenarioStepMapper stepMapper;
    private final TestVariableMapper variableMapper;
    private final TestEnvironmentMapper environmentMapper;
    private final RestTemplate restTemplate;
    private final ExpressionEvaluator evaluator;
    private final SecretResolver secretResolver;
    private final SeqProvider seqProvider;
    private final MessageParserRegistry parserRegistry;
    private final TestTargetGuard targetGuard;
    private final ProjectAuthz authz;
    /** 批量运行用的有界线程池（并发执行各条独立用例），由 AdminConfig 提供。 */
    private final ExecutorService batchExecutor;

    @Autowired
    public TestCaseService(TestCaseMapper caseMapper, TestRunLogMapper logMapper, TestRunRecordMapper recordMapper,
                           TestScenarioStepMapper stepMapper, TestVariableMapper variableMapper,
                           TestEnvironmentMapper environmentMapper,
                           RestTemplate restTemplate, ExpressionEvaluator evaluator,
                           SecretResolver secretResolver, SeqProvider seqProvider,
                           MessageParserRegistry parserRegistry, TestTargetGuard targetGuard,
                           ProjectAuthz authz,
                           @Qualifier("batchExecutor") ExecutorService batchExecutor) {
        this.caseMapper = caseMapper;
        this.logMapper = logMapper;
        this.recordMapper = recordMapper;
        this.stepMapper = stepMapper;
        this.variableMapper = variableMapper;
        this.environmentMapper = environmentMapper;
        this.restTemplate = restTemplate;
        this.evaluator = evaluator;
        this.secretResolver = secretResolver;
        this.seqProvider = seqProvider;
        this.parserRegistry = parserRegistry;
        this.targetGuard = targetGuard;
        this.authz = authz;
        this.batchExecutor = batchExecutor;
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
        // 数据驱动配置：之前漏了这条，前端提交的新 dataSet 被静默丢弃（改不动数据驱动）
        if (patch.getDataSet() != null) exists.setDataSet(patch.getDataSet());
        normalize(exists);
        caseMapper.updateById(exists);
        return exists;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        // 级联清理：引用本用例的场景步骤（用例已删则步骤无法运行，按 interface→rule 同模式清掉避免孤儿）
        stepMapper.delete(new LambdaQueryWrapper<TestScenarioStep>().eq(TestScenarioStep::getCaseId, id));
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
        stepMapper.delete(new LambdaQueryWrapper<TestScenarioStep>().in(TestScenarioStep::getCaseId, ids));
        logMapper.delete(new LambdaQueryWrapper<TestRunLog>().in(TestRunLog::getCaseId, ids));
        caseMapper.deleteBatchIds(ids);
    }

    /**
     * 批量运行：同项目多用例，项目变量/环境变量只加载一次，逐条执行(proxy)+落历史，返回每条摘要与汇总。
     * 摘要不含完整响应体（大批量回传过大），需查详情走单条运行/历史。
     *
     * <p>各条用例彼此独立（HTTP 转发 / TCP 一问一答），串行时总耗时 = Σ 单条耗时，
     * 选 50 条慢接口就得等好几分钟。故用有界线程池并发执行，并按入参顺序回填结果
     * （前端表格顺序不随完成先后跳动）。
     *
     * <p><b>鉴权必须在调用线程完成</b>：{@link AuthContext} 是 ThreadLocal，
     * 工作线程里取不到当前登录用户，若把「取用例 + 成员校验」放进工作线程会一律报无权限。
     */
    public BatchRunResult batchRun(List<Long> ids, Long projectId, Long envId) {
        BatchRunResult res = new BatchRunResult();
        List<Map<String, Object>> results = new ArrayList<>();
        res.setResults(results);
        if (ids == null || ids.isEmpty()) {
            return res;
        }
        Map<String, Object> baseVars;
        String baseVarsError = null;
        try {
            baseVars = buildBaseVars(projectId, envId);
        } catch (Exception e) {
            // 环境跨项目等上下文初始化失败：不能静默用空变量继续跑（结果会失真且掩盖错误）
            baseVars = new HashMap<>();
            baseVarsError = rootMessage(e);
        }
        long t0 = System.currentTimeMillis();

        int n = ids.size();
        // 先在调用线程逐条取用例（内含成员鉴权），失败只影响该条，不影响其它条
        List<Map<String, Object>> items = new ArrayList<>(n);
        List<TestCase> cases = new ArrayList<>(n);
        List<String> preErrors = new ArrayList<>(n);
        for (Long id : ids) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            TestCase tc = null;
            String preError = null;
            try {
                tc = get(id); // 取全量 + requireMember
                item.put("name", tc.getName());
                // 用例必须属于本项目：否则会把 A 项目的用例放到 B 项目的环境下跑，
                // 变量/环境变量全串了，结果既失真又可能泄露跨项目数据
                if (projectId != null && tc.getProjectId() != null && !projectId.equals(tc.getProjectId())) {
                    preError = "用例不属于当前项目";
                }
            } catch (Exception e) {
                preError = rootMessage(e);
            }
            items.add(item);
            cases.add(tc);
            preErrors.add(preError);
        }

        final Map<String, Object> finalBaseVars = baseVars;
        List<Future<Void>> futures = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final TestCase tc = cases.get(i);
            final String preError = preErrors.get(i);
            final Map<String, Object> item = items.get(i);
            if (tc == null || preError != null) {
                if (preError != null) {
                    item.put("passed", false);
                    item.put("error", preError);
                }
                continue;
            }
            if (baseVarsError != null) {
                item.put("passed", false);
                item.put("error", "上下文初始化失败: " + baseVarsError);
                continue;
            }
            futures.add(batchExecutor.submit(() -> {
                try {
                    EvalContext ctx = newContext(projectId, new HashMap<>(finalBaseVars));
                    RunResult rr = executeCase(tc, ctx);
                    writeLog(tc, "proxy", rr);
                    item.put("passed", rr.getPassed());
                    item.put("httpStatus", rr.getHttpStatus());
                    item.put("costMs", rr.getCostMs());
                    item.put("error", rr.getError());
                    item.put("warning", rr.getWarning());
                } catch (Exception e) {
                    item.put("passed", false);
                    item.put("error", rootMessage(e));
                }
                return null;
            }));
        }
        for (Future<Void> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                // 单条任务异常不应中断整批；该条的错误已在任务内捕获
                log.debug("批量运行单条任务异常: {}", rootMessage(e));
            }
        }

        int passed = 0;
        for (Map<String, Object> item : items) {
            results.add(item);
            if (Boolean.TRUE.equals(item.get("passed"))) {
                passed++;
            }
        }
        res.setTotal(results.size());
        res.setPassedCount(passed);
        res.setFailedCount(results.size() - passed);
        res.setCostMs(System.currentTimeMillis() - t0);
        res.setPassed(!results.isEmpty() && passed == results.size());
        return res;
    }

    /** 批量启用/停用：先逐条鉴权全部通过，再统一改状态（避免半成功）。 */
    @Transactional
    public void setBatchStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<TestCase> cases = caseMapper.selectBatchIds(ids);
        for (TestCase t : cases) {
            authz.requireMember(t.getProjectId());
        }
        TestCase upd = new TestCase();
        upd.setStatus(status);
        caseMapper.update(upd, new LambdaQueryWrapper<TestCase>().in(TestCase::getId, ids));
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
            // 记录查看：请求/返回报文一并入库，便于回看每行数据跑出来的现场
            d.put("request", rr.getRequest());
            d.put("response", rr.getResponse());
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
        // GET/HEAD 不允许带请求体：HttpURLConnection 会抛 ProtocolException
        // ("Cannot write to a URLConnection if using HTTP/1.1")，错误信息完全看不出
        // 是用例配了 body。这里显式丢弃 body，并给出可读的提示。
        if (reqBody.body != null && (HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method))) {
            reqBody.body = null;
            reqBody.contentType = null;
            // 用 warning 而非 error：请求本身是成功的，不能因此把用例判为失败
            rr.setWarning("提示：GET/HEAD 请求不携带请求体，已忽略用例中配置的 body");
        }
        long t0 = System.currentTimeMillis();
        try {
            if (reqBody.contentType != null && !httpHeaders.containsKey("Content-Type")) {
                httpHeaders.setContentType(reqBody.contentType);
            }
            // 记录请求报文（在真正发出前抓取，含求值后的最终值）
            rr.setRequest(describeHttpRequest(method, fullUrl, httpHeaders, reqBody));
            RequestEntity<Object> req = new RequestEntity<>(reqBody.body, httpHeaders, method, new URI(fullUrl));
            ResponseEntity<String> resp = restTemplate.exchange(req, String.class);
            rr.setHttpStatus(resp.getStatusCodeValue());
            rr.setHeaders(flatten(resp.getHeaders()));
            rr.setBody(resp.getBody());
        } catch (Exception e) {
            rr.setError("请求失败: " + rootMessage(e));
        }
        rr.setCostMs(System.currentTimeMillis() - t0);
        rr.setResponse(describeResponse(rr));
    }

    /**
     * 描述 HTTP 请求报文：方法 + URL + 请求头 + 请求体。
     * 用于「记录查看」——回看某次运行实际发出的请求。
     */
    private Map<String, Object> describeHttpRequest(HttpMethod method, String url,
                                                    HttpHeaders headers, RequestBody body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("protocol", "HTTP");
        m.put("method", method == null ? "" : method.name());
        m.put("url", url);
        m.put("headers", flatten(headers));
        m.put("body", describeBody(body == null ? null : body.body));
        return m;
    }

    /** 请求体可读化：字符串原样；二进制给 hex 预览；表单等结构给 JSON。 */
    private String describeBody(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof String) {
            return (String) body;
        }
        if (body instanceof byte[]) {
            byte[] b = (byte[]) body;
            String hex = com.miragemock.dsl.crypto.Codec.hex(b);
            if (hex.length() > 512) {
                hex = hex.substring(0, 512) + "...(共 " + b.length + " 字节)";
            }
            return "[二进制 " + b.length + " 字节] " + hex;
        }
        try {
            return JsonUtils.toJson(body);
        } catch (Exception e) {
            return String.valueOf(body);
        }
    }

    /** 返回报文：响应头 + 响应体 */
    private Map<String, Object> describeResponse(RunResult rr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("headers", rr.getHeaders() == null ? new LinkedHashMap<String, Object>() : rr.getHeaders());
        m.put("body", rr.getBody());
        return m;
    }

    /**
     * TCP 执行：按 tcp_config 加帧发送 → 按帧配置精确读取一帧 → 按报文格式解析为字段 JSON。
     *
     * <p>要点：
     * <ul>
     *   <li>读取按 {@code frame_config} 精确取一帧（{@link FrameReader}），
     *       不再「读到 EOF/超时」——否则长连接监听器每次都要白等满 SO_TIMEOUT。</li>
     *   <li>目标主机先经 SSRF 策略解析并<b>校验</b>，再用校验通过的 IP 建立连接，
     *       消除「校验时解析到公网、连接时解析到内网」的 DNS rebinding 窗口。</li>
     *   <li>端口做范围校验，避免 IllegalArgumentException 直接冒泡成无意义错误。</li>
     * </ul>
     */
    private void executeTcp(TestCase tc, EvalContext ctx, RunResult rr) {
        long t0 = System.currentTimeMillis();
        try {
            Map<String, Object> tcpCfg = parseTcpConfig(tc.getTcpConfig());
            String frameConfig = normalizeFrameConfig(tcpCfg.get("frameConfig"));
            String messageFormat = str(tcpCfg.get("messageFormat"));
            if (messageFormat.isEmpty()) {
                messageFormat = "json";
            }
            Map<String, Object> formatConfig = normalizeFormatConfig(tcpCfg.get("formatConfig"));

            String target = eval(tc.getUrl() == null ? "" : tc.getUrl().trim(), ctx);
            int colon = target.lastIndexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("目标应为 host:port：" + target);
            }
            String host = target.substring(0, colon).trim();
            int port = parsePort(target.substring(colon + 1).trim(), target);
            if (host.isEmpty()) {
                host = "localhost";
            }
            // 解析并校验（返回已校验的 IP，连接时直接用它，规避 DNS rebinding）
            InetAddress addr = targetGuard.resolveAllowed(host);

            Map<String, Object> fields = JsonUtils.parseMap(eval(tc.getBody() == null ? "{}" : tc.getBody(), ctx));
            if (fields == null) {
                fields = new LinkedHashMap<>();
            }
            MessageParser parser = parserRegistry.resolve(messageFormat);
            byte[] payload = parser.encode(fields, formatConfig);
            byte[] framed = FrameEncoder.encode(payload, frameConfig);
            // 记录请求报文（字段文本 + 加帧后的线路字节）
            rr.setRequest(describeTcpRequest(host, port, frameConfig, messageFormat, fields, framed));
            byte[] resp = tcpRoundTrip(addr, port, framed, frameConfig);
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
        rr.setResponse(describeResponse(rr));
    }

    /**
     * 描述 TCP 请求报文：目标 + 帧配置 + 字段文本 + 加帧后的线路字节（hex）。
     * raw 是真正发到线上的字节，能直观看出长度头/分隔符/定长补位。
     */
    private Map<String, Object> describeTcpRequest(String host, int port, String frameConfig,
                                                   String messageFormat, Map<String, Object> fields,
                                                   byte[] framed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("protocol", "TCP");
        m.put("target", host + ":" + port);
        m.put("frame", frameConfig);
        m.put("messageFormat", messageFormat);
        m.put("body", fields == null || fields.isEmpty() ? null : JsonUtils.toJson(fields));
        m.put("raw", com.miragemock.dsl.crypto.Codec.hex(framed));
        m.put("rawLength", framed == null ? 0 : framed.length);
        return m;
    }

    /**
     * 端口解析与范围校验（1..65535）。
     *
     * <p>原先直接 {@code Integer.parseInt}：非法值会抛裸 NumberFormatException，
     * 端口越界（如 70000）要等到 connect 阶段才报错，错误信息难以定位。
     */
    private int parsePort(String raw, String target) {
        int port;
        try {
            port = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("端口不是合法数字：" + target);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("端口超出合法范围(1-65535)：" + port);
        }
        return port;
    }

    /**
     * frameConfig 归一化：兼容「JSON 字符串」与「嵌套对象」两种写法。
     *
     * <p>UI 的 tcp_config 模板写的是嵌套对象
     * （{"frameConfig":{"type":"length_field",...}}），
     * 而「从监听器填充」写入的是 JSON 字符串
     * （{"frameConfig":"{\"type\":\"length_field\",...}"}）。
     * 原实现用 {@code String.valueOf(map)} 取值，嵌套对象会变成
     * {@code {type=length_field, lenBytes=4}} 这种非 JSON 文本 → 帧配置被静默忽略。
     */
    private String normalizeFrameConfig(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Map) {
            return JsonUtils.toJson(v);
        }
        String s = v.toString().trim();
        if (s.isEmpty() || s.equals("{}")) {
            return "";
        }
        return s;
    }

    /** formatConfig 归一化：JSON 字符串 → Map；已是 Map 则直接用。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeFormatConfig(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Map) {
            return (Map<String, Object>) v;
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return JsonUtils.parseMap(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("formatConfig 不是合法 JSON 对象");
        }
    }

    /**
     * TCP 一问一答：发完 shutdown 输出（触发 close_end/短连接回写），
     * 按帧配置精确读取一帧响应。
     *
     * @param addr        已通过 SSRF 校验的目标 IP（直接用它连接，避免二次解析）
     * @param frameConfig 帧配置 JSON 文本（用于精确读取一帧）
     */
    private byte[] tcpRoundTrip(InetAddress addr, int port, byte[] request, String frameConfig) throws IOException {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(addr, port), 5000);
            sock.setSoTimeout(TCP_READ_TIMEOUT_MS);
            OutputStream out = sock.getOutputStream();
            out.write(request);
            out.flush();
            sock.shutdownOutput();
            return FrameReader.readFrame(sock, frameConfig, TCP_READ_TIMEOUT_MS);
        }
    }

    private void evalAssertions(RunResult rr, TestCase tc, EvalContext ctx) {
        boolean allPassed = true;
        boolean tcp = "TCP".equalsIgnoreCase(tc.getProtocol());
        for (Map<String, Object> a : parseList(tc.getAssertions())) {
            a.put("expected", eval(str(a.get("expected")), ctx));
            Map<String, Object> r = evalAssertion(a,
                    rr.getHttpStatus() == null ? 0 : rr.getHttpStatus(), rr.getHeaders(), rr.getBody(), rr.getCostMs());
            // TCP 没有响应头概念：header / headerExists 断言在 TCP 用例上恒为「不通过」，
            // 用户只能看到一条永远失败的断言。此类断言标记为「跳过」，不计入整体结论。
            if (tcp && isTcpInapplicableAssertion(str(a.get("type")))) {
                r.put("passed", Boolean.TRUE);
                r.put("skipped", Boolean.TRUE);
                r.put("message", "TCP 协议无响应头/状态码，该断言已跳过");
            }
            rr.getAssertions().add(r);
            if (!Boolean.TRUE.equals(r.get("passed"))) {
                allPassed = false;
            }
        }
        rr.setPassed(allPassed && rr.getError() == null);
    }

    /** TCP 用例上不适用的断言类型：响应头相关 + HTTP 状态码。 */
    private boolean isTcpInapplicableAssertion(String type) {
        return "header".equalsIgnoreCase(type)
                || "headerExists".equalsIgnoreCase(type)
                || "status".equalsIgnoreCase(type);
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
        // 鉴权：运行历史按 caseId 直查，必须先校验当前用户是该用例所属项目的成员
        get(caseId);
        return logMapper.selectList(new LambdaQueryWrapper<TestRunLog>()
                .eq(TestRunLog::getCaseId, caseId)
                .orderByDesc(TestRunLog::getCreateTime)
                .last("LIMIT " + HISTORY_LIMIT));
    }

    /**
     * 单条运行记录详情（含请求/返回报文）。
     *
     * <p>报文三列在实体上标了 {@code select = false}（列表不查，避免 100 条大报文把响应撑爆），
     * 这里显式 select 出来。
     */
    public TestRunLog runDetail(Long caseId, Long runId) {
        // 鉴权：同 runs()
        get(caseId);
        TestRunLog log = logMapper.selectOne(new LambdaQueryWrapper<TestRunLog>()
                .select(TestRunLog::getId, TestRunLog::getProjectId, TestRunLog::getCaseId,
                        TestRunLog::getMode, TestRunLog::getHttpStatus, TestRunLog::getCostMs,
                        TestRunLog::getPassed, TestRunLog::getAssertionResult, TestRunLog::getError,
                        TestRunLog::getRequestInfo, TestRunLog::getResponseBody, TestRunLog::getResponseHeaders,
                        TestRunLog::getCreateTime, TestRunLog::getUpdateTime)
                .eq(TestRunLog::getId, runId)
                .eq(TestRunLog::getCaseId, caseId));
        if (log == null) {
            throw new BizException(ResultCode.NOT_FOUND, "运行记录不存在: " + runId);
        }
        return log;
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
                // 环境必须属于当前项目：否则可用 A 项目的 envId 读取 B 项目的 baseUrl/变量
                // （跨项目 IDOR）。不属于时直接忽略该环境。
                if (projectId != null && env.getProjectId() != null
                        && !projectId.equals(env.getProjectId())) {
                    throw new BizException(ResultCode.FORBIDDEN, "环境不属于当前项目");
                }
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
        authz.requireMember(exists.getProjectId());
        if (patch.getVarValue() != null) exists.setVarValue(patch.getVarValue());
        if (patch.getRemark() != null) exists.setRemark(patch.getRemark());
        if (patch.getStatus() != null) exists.setStatus(patch.getStatus());
        variableMapper.updateById(exists);
        return exists;
    }

    @Transactional
    public void deleteVariable(Long id) {
        TestVariable exists = variableMapper.selectById(id);
        if (exists == null) {
            throw new BizException(ResultCode.NOT_FOUND, "变量不存在");
        }
        authz.requireMember(exists.getProjectId());
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
        // 记录查看：请求/返回报文入库（截断防超大报文把库撑爆）
        log.setRequestInfo(rr.getRequest() == null ? null : JsonUtils.toJson(truncateValues(rr.getRequest())));
        log.setResponseBody(truncate(rr.getBody()));
        log.setResponseHeaders(rr.getHeaders() == null ? null : JsonUtils.toJson(rr.getHeaders()));
        logMapper.insert(log);
    }

    /**
     * 逐个字段截断请求报文里的长文本（body / raw）。
     *
     * <p><b>不能对序列化后的 JSON 整体截断</b>：那样会从中间切断，
     * 存进去的是非法 JSON，前端解析失败 → 整条请求报文都看不到（比截断更糟）。
     * 这里保持外层 JSON 完整，只把超长字符串值裁掉。
     */
    private Map<String, Object> truncateValues(Map<String, Object> src) {
        if (src == null || src.isEmpty()) {
            return src;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            Object v = e.getValue();
            out.put(e.getKey(), v instanceof String ? truncate((String) v) : v);
        }
        return out;
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
