package org.ssdd.health;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;

@RestController
public class HealthController {
    private final String version;
    public HealthController(@Value("${ssdd.version}") String version) { this.version = version; }

    @Operation(summary = "基础存活检查", description = "不代表推理模型已经就绪；数据库可用性参见 readiness")
    @GetMapping("/health")
    public HealthResponse health() { return new HealthResponse("ok", Instant.now(), version); }

    public record HealthResponse(String status, Instant timestamp, String version) {}
}
