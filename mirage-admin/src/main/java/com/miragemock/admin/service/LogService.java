package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miragemock.admin.mapper.ApiInterfaceMapper;
import com.miragemock.admin.mapper.MockRequestLogMapper;
import com.miragemock.admin.mapper.MockRuleMapper;
import com.miragemock.admin.mapper.ProjectMapper;
import com.miragemock.common.api.PageResult;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRequestLog;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.entity.Project;
import com.miragemock.common.entity.TestCase;
import com.miragemock.common.exception.BizException;
import com.miragemock.common.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    private final MockRequestLogMapper logMapper;
    private final ApiInterfaceMapper interfaceMapper;
    private final MockRuleMapper ruleMapper;
    private final ProjectMapper projectMapper;

    @Autowired
    public LogService(MockRequestLogMapper logMapper, ApiInterfaceMapper interfaceMapper,
                      MockRuleMapper ruleMapper, ProjectMapper projectMapper) {
        this.logMapper = logMapper;
        this.interfaceMapper = interfaceMapper;
        this.ruleMapper = ruleMapper;
        this.projectMapper = projectMapper;
    }

    public PageResult<MockRequestLog> query(Long projectId, Long interfaceId, Integer matched, String protocol,
                                            String keyword, Long from, Long to, long page, long size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 500) {
            size = 20;
        }
        LambdaQueryWrapper<MockRequestLog> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(MockRequestLog::getProjectId, projectId);
        }
        if (interfaceId != null) {
            wrapper.eq(MockRequestLog::getInterfaceId, interfaceId);
        }
        if (matched != null) {
            wrapper.eq(MockRequestLog::getMatched, matched);
        }
        if (protocol != null && !protocol.isEmpty()) {
            wrapper.eq(MockRequestLog::getProtocol, protocol);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            // 请求原文含 方法/路径/头/体，关键字模糊匹配它即可定位
            wrapper.like(MockRequestLog::getRequestRaw, keyword.trim());
        }
        if (from != null) {
            wrapper.ge(MockRequestLog::getCreateTime, toLocalDateTime(from));
        }
        if (to != null) {
            wrapper.le(MockRequestLog::getCreateTime, toLocalDateTime(to));
        }
        wrapper.orderByDesc(MockRequestLog::getCreateTime);
        // 列表瘦身：不回传 requestRaw/requestParsed/responseRaw 三段大文本，详情按需走 get(logId)
        wrapper.select(MockRequestLog::getId, MockRequestLog::getProjectId, MockRequestLog::getInterfaceId,
                MockRequestLog::getRuleId, MockRequestLog::getProtocol, MockRequestLog::getClientAddr,
                MockRequestLog::getMatched, MockRequestLog::getCostMs, MockRequestLog::getCreateTime);

        Page<MockRequestLog> result = logMapper.selectPage(new Page<>(page, size), wrapper);
        enrichNames(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    /** 单条完整日志（含三段原文），供详情弹窗按需懒加载，避免列表全量回传大文本。 */
    public MockRequestLog get(Long logId) {
        MockRequestLog lg = logMapper.selectById(logId);
        if (lg == null) {
            throw new BizException(ResultCode.NOT_FOUND, "日志不存在");
        }
        return lg;
    }

    /** 回填项目/接口/规则名称，前端展示名称而非 id */
    private void enrichNames(List<MockRequestLog> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ifaceIds = new HashSet<>();
        Set<Long> ruleIds = new HashSet<>();
        Set<Long> projIds = new HashSet<>();
        for (MockRequestLog l : records) {
            if (l.getInterfaceId() != null) ifaceIds.add(l.getInterfaceId());
            if (l.getRuleId() != null) ruleIds.add(l.getRuleId());
            if (l.getProjectId() != null) projIds.add(l.getProjectId());
        }
        Map<Long, String> ifaceNames = ifaceIds.isEmpty() ? new HashMap<>()
                : toNameMap(interfaceMapper.selectBatchIds(ifaceIds), ApiInterface::getId, ApiInterface::getName);
        Map<Long, String> ruleNames = ruleIds.isEmpty() ? new HashMap<>()
                : toNameMap(ruleMapper.selectBatchIds(ruleIds), MockRule::getId, MockRule::getName);
        Map<Long, String> projNames = projIds.isEmpty() ? new HashMap<>()
                : toNameMap(projectMapper.selectBatchIds(projIds), Project::getId, Project::getName);
        for (MockRequestLog l : records) {
            l.setInterfaceName(ifaceNames.get(l.getInterfaceId()));
            l.setRuleName(ruleNames.get(l.getRuleId()));
            l.setProjectName(projNames.get(l.getProjectId()));
        }
    }

    private <T> Map<Long, String> toNameMap(List<T> list, java.util.function.Function<T, Long> idFn,
                                            java.util.function.Function<T, String> nameFn) {
        Map<Long, String> m = new HashMap<>();
        if (list == null) {
            return m;
        }
        for (T t : list) {
            Long id = idFn.apply(t);
            if (id != null) {
                m.put(id, nameFn.apply(t));
            }
        }
        return m;
    }

    /** 每天凌晨 3 点清理 7 天前的日志 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanup() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        int deleted = logMapper.delete(new LambdaQueryWrapper<MockRequestLog>()
                .lt(MockRequestLog::getCreateTime, threshold));
        log.info("清理 7 天前请求日志，删除 {} 条", deleted);
    }

    private LocalDateTime toLocalDateTime(Long epochMillis) {
        return new Date(epochMillis).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    // 保留 list 备用
    public List<MockRequestLog> list(Long projectId) {
        return logMapper.selectList(new LambdaQueryWrapper<MockRequestLog>()
                .eq(MockRequestLog::getProjectId, projectId)
                .orderByDesc(MockRequestLog::getCreateTime)
                .last("limit 100"));
    }

    // ===================== 请求日志 → 测试用例草稿 =====================

    /** 解析 HTTP 请求日志为「未保存」的测试用例草稿，供前端打开编辑、复核后再保存。 */
    public TestCase buildTestCaseDraft(Long projectId, Long logId) {
        MockRequestLog lg = logMapper.selectById(logId);
        if (lg == null) {
            throw new BizException(ResultCode.NOT_FOUND, "日志不存在");
        }
        if (lg.getProjectId() == null || !lg.getProjectId().equals(projectId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "日志不属于当前项目");
        }
        if (!"HTTP".equalsIgnoreCase(lg.getProtocol())) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅支持 HTTP 协议日志生成测试用例");
        }
        return parseHttpRequest(lg);
    }

    /** requestRaw 格式："METHOD /path?query\nH: V\n...\n\nbody"，分隔符 \n。包级可见以便单测。 */
    TestCase parseHttpRequest(MockRequestLog lg) {
        TestCase tc = new TestCase();
        tc.setProjectId(lg.getProjectId());
        tc.setMode("proxy");
        tc.setStatus(1);

        String raw = lg.getRequestRaw() == null ? "" : lg.getRequestRaw();
        if (raw.endsWith("...(truncated)")) {
            raw = raw.substring(0, raw.length() - "...(truncated)".length());
        }
        String[] lines = raw.split("\n", -1);

        // 第一行：METHOD /path?query
        String method = "GET";
        String path = "/";
        String queryString = "";
        if (lines.length > 0) {
            String line0 = lines[0].trim();
            int sp = line0.indexOf(' ');
            if (sp > 0) {
                method = line0.substring(0, sp).trim().toUpperCase();
                String target = line0.substring(sp + 1).trim();
                int q = target.indexOf('?');
                if (q >= 0) {
                    path = target.substring(0, q);
                    queryString = target.substring(q + 1);
                } else {
                    path = target;
                }
            }
        }
        if (path.isEmpty()) {
            path = "/";
        }

        // 头部直到空行
        List<Map<String, String>> headers = new ArrayList<>();
        String contentType = null;
        boolean multipart = false;
        int i = 1;
        for (; i < lines.length; i++) {
            String l = lines[i];
            if (l.isEmpty()) {
                i++;
                break;
            }
            int c = l.indexOf(':');
            if (c > 0) {
                String k = l.substring(0, c).trim();
                String v = l.substring(c + 1).trim();
                if (k.equalsIgnoreCase("content-type")) {
                    contentType = v.isEmpty() ? null : v;
                    multipart = v.toLowerCase().contains("multipart/form-data");
                }
                if (!DROP_HEADERS.contains(k.toLowerCase())) {
                    Map<String, String> h = new LinkedHashMap<>();
                    h.put("k", k);
                    h.put("v", v);
                    headers.add(h);
                }
            }
        }

        // 剩余为请求体
        StringBuilder body = new StringBuilder();
        for (; i < lines.length; i++) {
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(lines[i]);
        }
        String bodyStr = body.toString();
        boolean hasBody = !bodyStr.trim().isEmpty();

        // 按 Content-Type 归类请求体类型
        String bodyType = "none";
        String bodyField = "";
        String bodyContentType = null;
        String ctLow = contentType == null ? "" : contentType.toLowerCase();
        if (hasBody) {
            if (multipart) {
                bodyType = "none"; // multipart 无法从文本日志重建
            } else if (ctLow.contains("application/x-www-form-urlencoded")) {
                bodyType = "x-www-form-urlencoded";
                bodyField = JsonUtils.toJson(parsePairs(bodyStr));
            } else {
                bodyType = "raw";
                bodyField = bodyStr;
                bodyContentType = contentType;
            }
        }

        List<Map<String, String>> queryParams = parsePairs(queryString);

        // 默认断言：HTTP 200
        List<Map<String, String>> asserts = new ArrayList<>();
        Map<String, String> a = new LinkedHashMap<>();
        a.put("type", "status");
        a.put("target", "");
        a.put("op", "eq");
        a.put("expected", "200");
        asserts.add(a);

        tc.setMethod(method);
        tc.setUrl(path);
        tc.setHeaders(JsonUtils.toJson(headers));
        tc.setQuery(JsonUtils.toJson(queryParams));
        tc.setBodyType(bodyType);
        tc.setBody(bodyField);
        tc.setBodyContentType(bodyContentType);
        tc.setAssertions(JsonUtils.toJson(asserts));
        tc.setName("日志转用例 · " + method + " " + path);
        StringBuilder remark = new StringBuilder("由请求日志 #").append(lg.getId())
                .append(" 自动生成；URL 为相对路径，请选择带 baseUrl 的环境或改为绝对地址后运行");
        if (multipart && hasBody) {
            remark.append("；原始请求为 multipart，请求体未重建，请手动补充");
        }
        tc.setRemark(remark.toString());
        return tc;
    }

    /** 解析 "a=1&b=two" → [{k,v}]，URL 解码（+ 视为空格）。 */
    private List<Map<String, String>> parsePairs(String qs) {
        List<Map<String, String>> out = new ArrayList<>();
        if (qs == null || qs.isEmpty()) {
            return out;
        }
        for (String pair : qs.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            Map<String, String> m = new LinkedHashMap<>();
            m.put("k", urlDecode(k));
            m.put("v", urlDecode(v));
            out.add(m);
        }
        return out;
    }

    private String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    /** 转用例时丢弃的头：hop-by-hop / 自动重算 / 由 body 决定，避免重放时冲突。 */
    private static final Set<String> DROP_HEADERS = new HashSet<>(Arrays.asList(
            "host", "content-length", "connection", "transfer-encoding",
            "accept-encoding", "content-type", "keep-alive"));
}
