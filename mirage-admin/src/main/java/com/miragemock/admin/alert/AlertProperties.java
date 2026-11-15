package com.miragemock.admin.alert;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 调度失败告警配置（mirage.alert）。
 *
 * <p>type 支持：dingtalk（钉钉机器人 markdown）/ wecom（企业微信机器人 text）/ generic（通用 JSON POST）。</p>
 */
@Data
@ConfigurationProperties(prefix = "mirage.alert")
public class AlertProperties {

    /** 是否启用告警（默认关闭，避免影响现有部署） */
    private boolean enabled = false;

    /** 机器人 webhook 地址 */
    private String webhookUrl;

    /** dingtalk / wecom / generic */
    private String type = "generic";

    /** 钉钉安全设置「自定义关键字」时，文案需包含该关键字 */
    private String keyword;
}
