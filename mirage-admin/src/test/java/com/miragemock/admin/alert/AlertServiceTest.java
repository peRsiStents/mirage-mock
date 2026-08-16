package com.miragemock.admin.alert;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 告警 payload 构造单测：钉钉 markdown / 企业微信 text / generic JSON 三种格式。
 */
class AlertServiceTest {

    private AlertService service(String type, String keyword) {
        AlertProperties props = new AlertProperties();
        props.setEnabled(true);
        props.setWebhookUrl("http://hook.example.com");
        props.setType(type);
        props.setKeyword(keyword);
        return new AlertService(props, null);
    }

    @Test
    void dingtalkMarkdownPayload() {
        Map<String, Object> payload = service("dingtalk", "回归").buildPayload("标题", "内容");
        assertEquals("markdown", payload.get("msgtype"));
        @SuppressWarnings("unchecked")
        Map<String, Object> md = (Map<String, Object>) payload.get("markdown");
        assertEquals("标题", md.get("title"));
        String text = (String) md.get("text");
        assertTrue(text.contains("回归"), "钉钉自定义关键字需出现在文案中");
        assertTrue(text.contains("内容"));
    }

    @Test
    void wecomTextPayload() {
        Map<String, Object> payload = service("wecom", null).buildPayload("标题", "内容");
        assertEquals("text", payload.get("msgtype"));
        @SuppressWarnings("unchecked")
        Map<String, Object> t = (Map<String, Object>) payload.get("text");
        String content = (String) t.get("content");
        assertTrue(content.startsWith("标题"));
        assertTrue(content.contains("内容"));
    }

    @Test
    void genericPayload() {
        Map<String, Object> payload = service("generic", null).buildPayload("标题", "内容");
        assertEquals("标题", payload.get("title"));
        assertEquals("内容", payload.get("content"));
        assertNull(payload.get("msgtype"));
    }

    @Test
    void disabledSkipsSend() {
        AlertProperties props = new AlertProperties();
        props.setEnabled(false);
        AlertService svc = new AlertService(props, null);
        // 未启用时 send 直接返回（不抛异常、不触碰 restTemplate）
        svc.send("t", "c");
    }

    @Test
    void noWebhookSkipsSend() {
        AlertProperties props = new AlertProperties();
        props.setEnabled(true);
        props.setWebhookUrl("");
        AlertService svc = new AlertService(props, null);
        svc.send("t", "c");
    }

    @Test
    void keywordOnlyWhenConfigured() {
        Map<String, Object> payload = service("generic", "【监控】").buildPayload("t", "c");
        assertFalse(payload.containsKey("keyword"));
    }
}
