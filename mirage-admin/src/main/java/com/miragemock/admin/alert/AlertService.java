package com.miragemock.admin.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 调度失败告警：按 type 构造钉钉/企业微信/通用 payload 异步 POST 到 webhook。
 * 发送失败仅记日志，不影响主流程。
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertProperties props;
    private final RestTemplate restTemplate;
    private final ExecutorService executor;

    @Autowired
    public AlertService(AlertProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.executor = new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500), new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    /**
     * 发送告警；未启用或无 webhook 时静默跳过。
     *
     * @param title   标题（钉钉 markdown title / 企微首行）
     * @param content 正文（markdown / 纯文本）
     */
    public void send(String title, String content) {
        if (!props.isEnabled() || props.getWebhookUrl() == null || props.getWebhookUrl().trim().isEmpty()) {
            return;
        }
        String url = props.getWebhookUrl().trim();
        executor.execute(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                restTemplate.postForEntity(url,
                        new HttpEntity<>(buildPayload(title, content), headers), String.class);
            } catch (Exception e) {
                log.warn("告警发送失败 url={}: {}", url, e.getMessage());
            }
        });
    }

    /** 按 type 构造 payload（包级可见便于单测）。 */
    Map<String, Object> buildPayload(String title, String content) {
        String keyword = props.getKeyword() == null ? "" : props.getKeyword();
        String text = keyword + (keyword.isEmpty() ? "" : "\n") + content;
        String type = props.getType() == null ? "generic" : props.getType();
        Map<String, Object> payload = new LinkedHashMap<>();
        switch (type) {
            case "dingtalk": {
                Map<String, Object> md = new LinkedHashMap<>();
                md.put("title", title);
                md.put("text", text);
                payload.put("msgtype", "markdown");
                payload.put("markdown", md);
                break;
            }
            case "wecom": {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("content", title + "\n" + text);
                payload.put("msgtype", "text");
                payload.put("text", t);
                break;
            }
            default: // generic
                payload.put("title", title);
                payload.put("content", content);
                break;
        }
        return payload;
    }
}
