package org.ssdd.cache;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("ssdd.stats-cache")
public record StatsCacheProperties(@NotNull Duration ttl) {
    public StatsCacheProperties {
        if(ttl!=null&&(ttl.compareTo(Duration.ofSeconds(1))<0||ttl.compareTo(Duration.ofMinutes(1))>0))throw new IllegalArgumentException("Stats cache TTL must be 1..60 seconds");
    }
}
