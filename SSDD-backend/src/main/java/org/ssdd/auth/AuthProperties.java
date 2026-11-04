package org.ssdd.auth;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("ssdd.auth")
public record AuthProperties(@NotBlank(message="请配置 SSDD_JWT_SIGNING_KEY") String signingKey,
        @NotBlank String issuer, @NotBlank String audience, @NotNull Duration accessTtl,
        @NotNull Duration refreshTtl, @Min(10) @Max(16) int bcryptStrength) {
    public AuthProperties {
        if (accessTtl != null && (accessTtl.isNegative() || accessTtl.isZero() || accessTtl.compareTo(Duration.ofHours(1)) > 0))
            throw new IllegalArgumentException("access-ttl 必须大于 0 且不超过 1 小时");
        if (refreshTtl != null && (refreshTtl.isNegative() || refreshTtl.isZero() || refreshTtl.compareTo(Duration.ofDays(30)) > 0))
            throw new IllegalArgumentException("refresh-ttl 必须大于 0 且不超过 30 天");
    }
    @Override public String toString() { return "AuthProperties[signingKey=REDACTED]"; }
}
