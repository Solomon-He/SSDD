package org.ssdd.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ssdd.bootstrap-admin")
public record BootstrapAdminProperties(boolean enabled, String email, String displayName, String password) {
    @Override public String toString() { return "BootstrapAdminProperties[enabled=" + enabled + ",credentials=REDACTED]"; }
}
