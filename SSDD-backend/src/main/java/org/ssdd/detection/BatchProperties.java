package org.ssdd.detection;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("ssdd.batch")
public record BatchProperties(@Min(1) @Max(20) int maxImages,@NotNull Duration processingTimeout) {
    public BatchProperties {
        if(processingTimeout!=null&&(processingTimeout.compareTo(Duration.ofSeconds(1))<0||processingTimeout.compareTo(Duration.ofSeconds(120))>0))throw new IllegalArgumentException("Batch processing budget must be 1..120 seconds");
    }
}
