package org.ssdd.task;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("ssdd.tasks")
public record TaskProperties(boolean workerEnabled,@Min(1) @Max(100) int capacity,@Min(1) @Max(5) int maxAttempts,
        @NotNull Duration pollInterval,@NotNull Duration retryDelay,@NotNull Duration attemptTimeout,
        @NotNull Duration maxAge,@NotNull Duration progressTtl) {
    public TaskProperties {
        range(pollInterval,Duration.ofMillis(50),Duration.ofSeconds(5));
        range(retryDelay,Duration.ofMillis(100),Duration.ofSeconds(30));
        range(attemptTimeout,Duration.ofSeconds(1),Duration.ofMinutes(30));
        range(maxAge,Duration.ofSeconds(2),Duration.ofHours(24));
        range(progressTtl,Duration.ofSeconds(1),Duration.ofMinutes(30));
    }
    private static void range(Duration value,Duration min,Duration max){if(value!=null&&(value.compareTo(min)<0||value.compareTo(max)>0))throw new IllegalArgumentException("Invalid async task duration");}
}
