package com.miragemock.tcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * TCP 自动配置：推送调度器。
 */
@Configuration
public class TcpAutoConfiguration {

    @Bean(name = "tcpTaskScheduler", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler tcpTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("mirage-tcp-push-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}
