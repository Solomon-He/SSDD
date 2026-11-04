package org.ssdd.inference;

import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;

@Component("modelInference")
public class InferenceHealthIndicator implements HealthIndicator {
    private final ModelRuntime runtime;
    public InferenceHealthIndicator(ModelRuntime runtime) { this.runtime=runtime; }
    @Override public Health health() {
        try { return Boolean.TRUE.equals(runtime.status().get("ready"))?Health.up().build():Health.down().build(); }
        catch(RuntimeException ex) { return Health.down().build(); }
    }
}
