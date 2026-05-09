package com.payment.bridge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class ExternalApiConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder,
                                     @Value("${payment.api.timeout.connect:5000}") long connectTimeout,
                                     @Value("${payment.api.timeout.read:2000}") long readTimeout) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout))
                .build();
    }
}
