package com.payment.bridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        ThreadFactory threadFactory = Thread.ofVirtual().name("payment-worker-%d", 0).factory();
        return Executors.newThreadPerTaskExecutor(threadFactory);
    }
}
