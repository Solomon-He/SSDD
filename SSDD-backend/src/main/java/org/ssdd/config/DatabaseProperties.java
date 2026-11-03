package org.ssdd.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ssdd.database")
public record DatabaseProperties(
        @NotBlank(message = "请配置 SSDD_DB_URL") String url,
        @NotBlank(message = "请配置 SSDD_DB_USERNAME 或 application-local.yml") String username,
        @NotBlank(message = "请配置 SSDD_DB_PASSWORD 或 application-local.yml") String password) {
    @Override
    public String toString() {
        return "DatabaseProperties[credentials=REDACTED]";
    }
}
