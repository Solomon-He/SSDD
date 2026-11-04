package org.ssdd.inference;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("ssdd.inference")
public record InferenceProperties(@NotNull Device device, @Min(0) int deviceId,
        @Min(1) @Max(4) int maxConcurrency, @Min(2) @Max(4) int maxLoadedSessions,
        @Min(1048576) long gpuMemoryLimitBytes, @NotNull Duration runTimeout) {
    public enum Device { CPU, CUDA, AUTO }
    public InferenceProperties {
        if (runTimeout != null && (runTimeout.compareTo(Duration.ofSeconds(1)) < 0
                || runTimeout.compareTo(Duration.ofMinutes(2)) > 0))
            throw new IllegalArgumentException("推理预算必须在 1～120 秒之间");
    }
}
