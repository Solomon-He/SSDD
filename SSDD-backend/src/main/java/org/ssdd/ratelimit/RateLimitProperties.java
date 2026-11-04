package org.ssdd.ratelimit;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("ssdd.rate-limit")
public record RateLimitProperties(boolean enabled,@NotBlank @Pattern(regexp="[a-zA-Z0-9:_-]{1,100}") String namespace,
        @NotNull Duration window,@Min(1) int loginIpLimit,@Min(1) int loginAccountLimit,
        @Min(1) int registerIpLimit,@Min(1) int refreshIpLimit,@Min(1) int maxFailures,@NotNull Duration cooldown) {
    public RateLimitProperties {
        if(window!=null&&(window.isZero()||window.isNegative()))throw new IllegalArgumentException("限流窗口必须大于 0");
        if(cooldown!=null&&(cooldown.isZero()||cooldown.isNegative()))throw new IllegalArgumentException("冷却时间必须大于 0");
    }
}
