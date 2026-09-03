package com.javaee.aiservice.config;

import com.alibaba.dashscope.utils.Constants;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** Configures the DashScope Java SDK's shared native API endpoint. */
@Configuration
public class DashScopeEndpointConfig {

    @Value("${spring.ai.dashscope.base-url}")
    private String baseUrl;

    @PostConstruct
    public void configureEndpoint() {
        Constants.baseHttpApiUrl = baseUrl;
    }
}
