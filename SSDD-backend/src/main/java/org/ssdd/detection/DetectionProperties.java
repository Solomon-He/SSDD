package org.ssdd.detection;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("ssdd.detection")
public record DetectionProperties(@Min(1) @Max(10485760) long maxFileBytes,
        @Min(1) @Max(16000000) int maxPixels, @Min(1) @Max(16384) int maxDimension,
        boolean rateLimitEnabled, @Min(1) int requestsPerWindow, @NotNull Duration rateWindow,
        @NotNull Duration resultUrlTtl, @NotNull Duration processingTimeout) {
    public DetectionProperties {
        if(rateWindow!=null&&(rateWindow.isNegative()||rateWindow.isZero()||rateWindow.compareTo(Duration.ofHours(1))>0))throw new IllegalArgumentException("Invalid detection rate window");
        if(resultUrlTtl!=null&&(resultUrlTtl.compareTo(Duration.ofSeconds(1))<0||resultUrlTtl.compareTo(Duration.ofMinutes(15))>0))throw new IllegalArgumentException("Invalid result URL lifetime");
        if(processingTimeout!=null&&(processingTimeout.compareTo(Duration.ofSeconds(1))<0||processingTimeout.compareTo(Duration.ofMinutes(2))>0))throw new IllegalArgumentException("Invalid detection processing budget");
    }
}
