package com.miragemock.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 批量执行相关参数（批量运行用例、批量导入等）。
 *
 * <p>批量运行会把选中的多条用例并发跑一遍（每条都是一次独立的 HTTP/TCP 往返），
 * 并发度需要可调：太高会瞬间打满目标服务/本机 mock 端口，太低则退化成串行。
 */
@Data
@ConfigurationProperties(prefix = "mirage.batch")
public class BatchProperties {

    /** 批量运行的并发度（同时执行的用例条数）。默认 8；设为 1 即串行。 */
    private int concurrency = 8;

    /** 批量执行的任务队列容量；队列满时由调用线程执行（背压，不丢任务）。 */
    private int queueCapacity = 512;
}
