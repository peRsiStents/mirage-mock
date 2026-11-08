package com.miragemock.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.miragemock.admin.security.JwtAuthFilter;
import com.miragemock.admin.security.JwtUtil;
import com.miragemock.admin.security.SecurityProperties;
import com.miragemock.admin.security.TestTargetProperties;
import com.miragemock.admin.security.TokenRevocationChecker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 管理端装配：安全属性 + 密码编码器 + JWT 鉴权过滤器注册。
 */
@Configuration
@EnableConfigurationProperties({ SecurityProperties.class, TestTargetProperties.class, BatchProperties.class })
public class AdminConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtUtil jwtUtil,
                                                                          TokenRevocationChecker revocationChecker) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(new JwtAuthFilter(jwtUtil, revocationChecker));
        reg.addUrlPatterns("/*");
        reg.setName("mirageJwtAuthFilter");
        // 在 Mock HTTP 过滤器之后执行；Mock 过滤器在 Mock 端口短路，不会触达此处
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return reg;
    }

    /**
     * 批量运行用例的线程池：有界队列 + CallerRuns 背压。
     *
     * <p>队列满时由提交线程（HTTP 请求线程）自己执行剩余任务：既不丢任务，
     * 也不会因并发度配置过大瞬间打满目标服务/本机 mock 端口。
     */
    @Bean(name = "batchExecutor", destroyMethod = "shutdown")
    public ExecutorService batchExecutor(BatchProperties props) {
        int c = Math.max(1, props.getConcurrency());
        int q = Math.max(1, props.getQueueCapacity());
        ThreadFactory tf = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "mirage-batch-" + seq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        return new ThreadPoolExecutor(c, c, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(q), tf, new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
