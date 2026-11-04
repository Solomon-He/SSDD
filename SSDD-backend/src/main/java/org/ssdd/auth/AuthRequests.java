package org.ssdd.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.Locale;

public final class AuthRequests {
    private AuthRequests() {}
    static String normalizeEmail(String email) { return email == null ? null : email.strip().toLowerCase(Locale.ROOT); }

    public record Register(@NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(min=6,max=72) String password, @Size(max=50) String displayName) {
        public Register { email = normalizeEmail(email); if (displayName != null) displayName = displayName.strip(); }
        @Override public String toString() { return "Register[credentials=REDACTED]"; }
    }
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {
        public Login { email = normalizeEmail(email); }
        @Override public String toString() { return "Login[credentials=REDACTED]"; }
    }
    public record Refresh(@JsonProperty("refresh_token") @NotBlank @Size(max=128) String refreshToken) {
        @Override public String toString() { return "Refresh[credentials=REDACTED]"; }
    }
    public record Password(@NotBlank @Size(max=72) String oldPassword, @NotBlank @Size(min=6,max=72) String newPassword) {
        @Override public String toString() { return "Password[credentials=REDACTED]"; }
    }
    public record Role(@NotBlank @Pattern(regexp="admin|inspector") String role) {}
}
