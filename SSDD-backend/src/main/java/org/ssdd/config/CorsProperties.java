package org.ssdd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties("ssdd.cors")
public record CorsProperties(List<String> allowedOrigins) {
    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of()
                : allowedOrigins.stream().filter(origin -> !origin.isBlank()).toList();
        for (String origin : allowedOrigins) {
            if (!origin.matches("https?://[^/\\s*]+")) {
                throw new IllegalArgumentException("CORS 来源必须是明确的 http/https origin，不允许通配符或路径");
            }
        }
    }
}
