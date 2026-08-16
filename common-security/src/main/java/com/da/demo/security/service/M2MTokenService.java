package com.da.demo.security.service;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class M2MTokenService {

    private static final Logger log = LoggerFactory.getLogger(M2MTokenService.class);

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;

    @Value("${keycloak.auth-server-url:http://localhost:8088}")
    private String keycloakUrl;

    @Value("${keycloak.realm:bus-reservation}")
    private String realm;

    @Value("${keycloak.internal-client-id:internal-backend-client}")
    private String internalClientId;

    @Value("${keycloak.internal-client-secret:internal-service-mesh-secret-key-123}")
    private String internalClientSecret;

    public M2MTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = new RestTemplate();
    }

    public String getInternalM2MToken() {
        String cacheKey = "cache:m2m_internal_token";

        // 1. Check Valkey cache first
        try {
            String cachedToken = redisTemplate.opsForValue().get(cacheKey);
            if (cachedToken != null && !cachedToken.isBlank()) {
                return cachedToken;
            }
        } catch (Exception e) {
            log.warn("Valkey cache read error for M2M token: {}", e.getMessage());
        }

        // 2. Fetch fresh token via OAuth2 Client Credentials grant
        try {
            String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, realm);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");
            formData.add("client_id", internalClientId);
            formData.add("client_secret", internalClientSecret);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("access_token")) {
                String token = (String) response.getBody().get("access_token");
                Number expiresIn = (Number) response.getBody().getOrDefault("expires_in", 300);

                // Cache with safety margin (expiresIn - 30 seconds)
                long ttl = Math.max(30, expiresIn.longValue() - 30);
                try {
                    redisTemplate.opsForValue().set(cacheKey, token, Duration.ofSeconds(ttl));
                } catch (Exception ignored) {}

                log.info("Acquired fresh M2M service token for {}", internalClientId);
                return token;
            }
        } catch (Exception e) {
            log.error("Failed to acquire M2M token from Keycloak for {}: {}", internalClientId, e.getMessage());
        }

        return null;
    }
}
