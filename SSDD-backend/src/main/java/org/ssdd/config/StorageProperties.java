package org.ssdd.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.nio.file.Path;

@Validated
@ConfigurationProperties("ssdd.storage")
public record StorageProperties(@NotNull Path root) {
    public StorageProperties {
        if (root != null) root = root.toAbsolutePath().normalize();
    }
}
