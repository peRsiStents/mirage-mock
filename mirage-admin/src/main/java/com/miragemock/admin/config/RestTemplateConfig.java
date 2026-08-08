package com.miragemock.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * RestTemplate：测试案例 proxy 模式转发用。
 * 设 10s 连接 / 30s 读超时；错误处理器置为不抛 4xx/5xx，便于统一拿到响应体。
 * 关闭自动跟随重定向：目标地址已由 TestTargetGuard 白名单校验，若放行 302 则可被诱导跳到内网地址，
 * 构成 SSRF 重定向绕过，故在此 fail-closed 不跟随（如需断言跳转可对 3xx 状态码/Location 头断言）。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        RestTemplate rt = new RestTemplate(factory);
        rt.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
                // no-op：交由调用方读取状态码与响应体
            }
        });
        return rt;
    }
}
