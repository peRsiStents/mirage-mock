package com.miragemock.http;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Mock HTTP 服务装配：在主端口之外增加一个独立 Tomcat 连接器承载 Mock 流量，
 * 并注册拦截过滤器（按端口短路）。
 */
@Configuration
@EnableConfigurationProperties(MirageHttpProperties.class)
public class MockServerConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> mockConnectorCustomizer(
            MirageHttpProperties props) {
        return factory -> {
            if (!props.isEnabled()) {
                return;
            }
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setPort(props.getPort());
            connector.setScheme("http");
            connector.setAttribute("address", "0.0.0.0");
            factory.addAdditionalTomcatConnectors(connector);
        };
    }

    @Bean
    public FilterRegistrationBean<MockHttpFilter> mockHttpFilterRegistration(
            MockHttpFilter mockHttpFilter) {
        FilterRegistrationBean<MockHttpFilter> reg = new FilterRegistrationBean<>(mockHttpFilter);
        reg.addUrlPatterns("/*");
        reg.setName("mirageMockHttpFilter");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }
}
