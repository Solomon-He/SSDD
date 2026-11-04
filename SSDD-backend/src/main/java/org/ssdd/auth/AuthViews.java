package org.ssdd.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public final class AuthViews {
    private AuthViews() {}
    public record Profile(String id, @JsonProperty("user_id") String userId, String role,
            @JsonProperty("display_name") String displayName, @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("created_at") Instant createdAt, @JsonProperty("updated_at") Instant updatedAt) {}
    public record User(String id, String email, Profile profile) {}
    public record Session(@JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken, @JsonProperty("expires_at") long expiresAt) {
        @Override public String toString() { return "Session[tokens=REDACTED]"; }
    }
    public record Login(User user, Session session) {}
    public record Registered(String userId) {}
    public record Avatar(@JsonProperty("avatar_url") String avatarUrl, Profile profile) {}
    public record AdminUser(String id, @JsonProperty("user_id") String userId, String role,
            @JsonProperty("display_name") String displayName, @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("created_at") Instant createdAt, @JsonProperty("updated_at") Instant updatedAt,
            String email, @JsonProperty("detection_count") Long detectionCount) {}
}
