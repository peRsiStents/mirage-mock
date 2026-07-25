package com.miragemock.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务线程池：供 @Scheduled 固定任务 + ScheduleManager 动态 CronTrigger 调度共用。
 */
@Configuration
public class SchedulerConfig {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(4);
        ts.setThreadNamePrefix("mirage-sched-");
        ts.setWaitForTasksToCompleteOnShutdown(false);
        return ts;
    }
}
