package com.payment.mock.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockPaymentProperties Configuration Validation")
class MockPaymentPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldFailContextStartupForInvalidFailureRate() {
        contextRunner.withPropertyValues(
                        "mock.payment.failure-rate=-0.5",
                        "mock.payment.delay.min=1",
                        "mock.payment.delay.max=10")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

    @Test
    void shouldFailContextStartupForInvalidDelayRange() {
        contextRunner.withPropertyValues(
                        "mock.payment.failure-rate=0.1",
                        "mock.payment.delay.min=100",
                        "mock.payment.delay.max=10")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

    @Configuration
    @EnableConfigurationProperties(MockPaymentProperties.class)
    static class TestConfig {
    }
}
