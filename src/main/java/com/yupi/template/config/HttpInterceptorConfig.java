package com.yupi.template.config;


import com.yupi.template.interceptor.SSEStreamInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * HTTP拦截器配置
 */
@Configuration
public class HttpInterceptorConfig {


    /**
     * 配置WebClient.Builde
     */
    @Bean
    @Primary
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                // SSE拦截器优先处理
                .filter(SSEStreamInterceptor.interceptResponse()) ;
    }
}
