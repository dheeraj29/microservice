package com.da.demo.security.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;

@Service
public class KeycloakAuthService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthService.class);

    private final RestTemplate restTemplate;

    @Value("${keycloak.auth-server-url:http://localhost:8088}")
    private String keycloakUrl;

    @Value("${keycloak.realm:bus-reservation}")
    private String realm;

    @Value("${keycloak.client-id:angular-client}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    public KeycloakAuthService() {
        this.restTemplate = new RestTemplate();
    }

    public Map<String, Object> exchangeAuthorizationCode(String code, String redirectUri) {
        return exchangeAuthorizationCode(code, redirectUri, null);
    }

    public Map<String, Object> exchangeAuthorizationCode(String code, String redirectUri, String codeVerifier) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", clientId);
        formData.add("code", code);
        formData.add("redirect_uri", redirectUri);
        if (codeVerifier != null && !codeVerifier.isBlank()) {
            formData.add("code_verifier", codeVerifier);
        }
        if (clientSecret != null && !clientSecret.isBlank()) {
            formData.add("client_secret", clientSecret);
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        return (Map<String, Object>) response.getBody();
    }

    public Map<String, Object> refreshAccessToken(String refreshToken) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", clientId);
        formData.add("refresh_token", refreshToken);
        if (clientSecret != null && !clientSecret.isBlank()) {
            formData.add("client_secret", clientSecret);
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        return (Map<String, Object>) response.getBody();
    }

    public void revokeToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            String revokeUrl = String.format("%s/realms/%s/protocol/openid-connect/revoke", keycloakUrl, realm);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("client_id", clientId);
            formData.add("token", refreshToken);
            formData.add("token_type_hint", "refresh_token");
            if (clientSecret != null && !clientSecret.isBlank()) {
                formData.add("client_secret", clientSecret);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
            restTemplate.postForEntity(revokeUrl, request, Void.class);
            log.info("Successfully revoked Keycloak token");
        } catch (Exception err) {
            log.warn("Token revocation note: {}", err.getMessage());
        }
    }

    public List<String> extractRolesFromToken(String accessToken) {
        List<String> roles = new ArrayList<>();
        try {
            JWT jwt = JWTParser.parse(accessToken);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Map<String, Object> realmAccess = (Map<String, Object>) claims.getClaim("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                List<String> rList = (List<String>) realmAccess.get("roles");
                for (String r : rList) {
                    roles.add(r);
                    if (!r.startsWith("ROLE_")) {
                        roles.add("ROLE_" + r);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Unable to parse roles from JWT: {}", e.getMessage());
        }
        return roles;
    }

    public String extractUsernameFromToken(String accessToken) {
        try {
            JWT jwt = JWTParser.parse(accessToken);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            String preferredUsername = claims.getStringClaim("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
            return claims.getSubject();
        } catch (Exception e) {
            return "authenticated_user";
        }
    }
}
