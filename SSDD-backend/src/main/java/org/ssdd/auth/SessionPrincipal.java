package org.ssdd.auth;

public record SessionPrincipal(String userId,String sessionId,String role) {}
