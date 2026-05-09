package com.payment.bridge.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.determineUrl());
        config.setUsername(properties.determineUsername());
        config.setPassword(properties.determinePassword());
        config.setDriverClassName(properties.determineDriverClassName());
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setPoolName("payment-bridge-hikari");
        config.setAutoCommit(false);
        return new HikariDataSource(config);
    }
}
