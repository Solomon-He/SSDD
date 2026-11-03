package org.ssdd.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationValidationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void missingCredentialsFailWithActionableHint() {
        runner.withPropertyValues("ssdd.database.url=jdbc:mysql://localhost/ssdd").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("SSDD_DB_USERNAME")
                    .hasStackTraceContaining("SSDD_DB_PASSWORD");
        });
    }

    @Test
    void rejectsCorsWildcards() {
        runner.withPropertyValues("ssdd.database.url=jdbc:mysql://localhost/ssdd",
                "ssdd.database.username=test", "ssdd.database.password=test",
                "ssdd.cors.allowed-origins[0]=*").run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({DatabaseProperties.class, CorsProperties.class})
    static class PropertiesConfiguration {}
}
