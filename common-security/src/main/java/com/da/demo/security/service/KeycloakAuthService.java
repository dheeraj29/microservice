package com.da.demo.security.service;

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

@Service
public class KeycloakAuthService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthService.class);

    private final RestTemplate restTemplate;
    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    @Value("${keycloak.auth-server-url:http://localhost:8088}")
    private String keycloakUrl;

    @Value("${keycloak.realm:bus-reservation}")
    private String realm;

    @Value("${keycloak.client-id:angular-client}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    @Value("${keycloak.internal-client-id:internal-backend-client}")
    private String internalClientId;

    @Value("${keycloak.internal-client-secret:internal-service-mesh-secret-key-123}")
    private String internalClientSecret;

    public KeycloakAuthService() {
        this.restTemplate = new RestTemplate();
    }

    public boolean introspectToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            String introspectUrl = String.format("%s/realms/%s/protocol/openid-connect/token/introspect", keycloakUrl, realm);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("token", token);
            formData.add("token_type_hint", "access_token");
            formData.add("client_id", internalClientId);
            formData.add("client_secret", internalClientSecret);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(introspectUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object active = response.getBody().get("active");
                return Boolean.TRUE.equals(active) || "true".equalsIgnoreCase(String.valueOf(active));
            }
            return false;
        } catch (Exception e) {
            log.warn("Token introspection with Keycloak note: {}", e.getMessage());
            return false;
        }
    }

    private synchronized ConfigurableJWTProcessor<SecurityContext> getJwtProcessor() {
        if (jwtProcessor == null) {
            try {
                URL jwkSetUrl = new URL(String.format("%s/realms/%s/protocol/openid-connect/certs", keycloakUrl, realm));
                JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(jwkSetUrl);
                JWSVerificationKeySelector<SecurityContext> keySelector =
                        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
                DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
                processor.setJWSKeySelector(keySelector);
                this.jwtProcessor = processor;
            } catch (Exception e) {
                log.error("Failed to initialize Keycloak JWKS processor: {}", e.getMessage());
            }
        }
        return jwtProcessor;
    }

    public JWTClaimsSet verifyAndDecodeJwt(String token) throws Exception {
        ConfigurableJWTProcessor<SecurityContext> processor = getJwtProcessor();
        if (processor != null) {
            JWTClaimsSet claims = processor.process(token, null);
            if (claims.getExpirationTime() != null && new Date().after(claims.getExpirationTime())) {
                throw new IllegalStateException("JWT token is expired");
            }
            return claims;
        }
        com.nimbusds.jwt.SignedJWT signedJWT = com.nimbusds.jwt.SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        if (claims.getExpirationTime() != null && new Date().after(claims.getExpirationTime())) {
            throw new IllegalStateException("JWT token is expired");
        }
        return claims;
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

    public String extractCustomClaim(String accessToken, String claimName, String defaultValue) {
        try {
            JWT jwt = JWTParser.parse(accessToken);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            String val = claims.getStringClaim(claimName);
            return (val != null && !val.isBlank()) ? val : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public void updateUserAttributesInKeycloak(String userAccessToken, String username, Map<String, String> attributes) {
        if (userAccessToken == null || userAccessToken.isBlank() || attributes == null || attributes.isEmpty()) {
            return;
        }

        // Pure Self-Service Least Privilege: User updates only their own account attributes
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(userAccessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            String accountUrl = String.format("%s/realms/%s/account", keycloakUrl, realm);
            HttpEntity<?> getReq = new HttpEntity<>(headers);
            ResponseEntity<Map> accResp = restTemplate.exchange(accountUrl, org.springframework.http.HttpMethod.GET, getReq, Map.class);

            if (accResp.getStatusCode().is2xxSuccessful() && accResp.getBody() != null) {
                Map<String, Object> accountData = new java.util.HashMap<>(accResp.getBody());
                Map<String, Object> existingAttributes = (Map<String, Object>) accountData.get("attributes");
                if (existingAttributes == null) {
                    existingAttributes = new java.util.HashMap<>();
                } else {
                    existingAttributes = new java.util.HashMap<>(existingAttributes);
                }

                for (Map.Entry<String, String> entry : attributes.entrySet()) {
                    existingAttributes.put(entry.getKey(), List.of(entry.getValue()));
                }
                accountData.put("attributes", existingAttributes);

                HttpEntity<Map<String, Object>> postReq = new HttpEntity<>(accountData, headers);
                restTemplate.exchange(accountUrl, org.springframework.http.HttpMethod.POST, postReq, Void.class);
                log.info("Successfully persisted user '{}' preferences to Keycloak using User Bearer Token: {}", username, attributes.keySet());
            }
        } catch (Exception e) {
            log.warn("User Bearer Token Keycloak Account API note for '{}': {}", username, e.getMessage());
        }
    }
}
