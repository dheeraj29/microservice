package com.da.demo.security.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String username;
    private List<String> roles = new ArrayList<>();
    private String accessToken;
    private String refreshToken;
    private Instant accessTokenExpiresAt;
    private long refreshSequence = 1L;
    private Instant createdAt = Instant.now();
    private Instant lastAccessedAt = Instant.now();
    private String clientFingerprint;
    private String language = "en";
    private String timezone = "Asia/Kolkata";
    private String homepage = "/booking";
    private String theme = "dark";

    public SessionRecord() {}

    @JsonIgnore
    public boolean isAccessTokenExpiring(long thresholdSeconds) {
        if (accessTokenExpiresAt == null) return true;
        return Instant.now().plusSeconds(thresholdSeconds).isAfter(accessTokenExpiresAt);
    }

    @JsonIgnore
    public boolean isAccessTokenValid() {
        if (accessTokenExpiresAt == null) return false;
        return Instant.now().isBefore(accessTokenExpiresAt);
    }

    public void incrementSequence() {
        this.refreshSequence++;
        this.lastAccessedAt = Instant.now();
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) { this.accessTokenExpiresAt = accessTokenExpiresAt; }

    public long getRefreshSequence() { return refreshSequence; }
    public void setRefreshSequence(long refreshSequence) { this.refreshSequence = refreshSequence; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }

    public String getClientFingerprint() { return clientFingerprint; }
    public void setClientFingerprint(String clientFingerprint) { this.clientFingerprint = clientFingerprint; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getHomepage() { return homepage; }
    public void setHomepage(String homepage) { this.homepage = homepage; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
}
