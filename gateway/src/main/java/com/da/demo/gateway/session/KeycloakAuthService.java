package com.da.demo.gateway.session;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;

import reactor.core.publisher.Mono;

@Service
public class KeycloakAuthService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthService.class);

    private final WebClient webClient;

    @Value("${keycloak.auth-server-url:http://localhost:8088}")
    private String keycloakUrl;

    @Value("${keycloak.realm:bus-reservation}")
    private String realm;

    @Value("${keycloak.client-id:angular-client}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    public KeycloakAuthService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<Map<String, Object>> exchangeAuthorizationCode(String code, String redirectUri) {
        return exchangeAuthorizationCode(code, redirectUri, null);
    }

    public Mono<Map<String, Object>> exchangeAuthorizationCode(String code, String redirectUri, String codeVerifier) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, realm);

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

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> (Map<String, Object>) map)
                .doOnError(err -> log.error("Failed to exchange authorization code with Keycloak: {}", err.getMessage()));
    }

    public Mono<Map<String, Object>> refreshAccessToken(String refreshToken) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, realm);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", clientId);
        formData.add("refresh_token", refreshToken);
        if (clientSecret != null && !clientSecret.isBlank()) {
            formData.add("client_secret", clientSecret);
        }

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> (Map<String, Object>) map)
                .doOnError(err -> log.error("Failed to refresh token with Keycloak: {}", err.getMessage()));
    }

    public Mono<Void> revokeToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.empty();
        }
        String revokeUrl = String.format("%s/realms/%s/protocol/openid-connect/revoke", keycloakUrl, realm);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("token", refreshToken);
        formData.add("token_type_hint", "refresh_token");
        if (clientSecret != null && !clientSecret.isBlank()) {
            formData.add("client_secret", clientSecret);
        }

        return webClient.post()
                .uri(revokeUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Successfully revoked Keycloak token"))
                .doOnError(err -> log.warn("Token revocation failed (session might already be terminated): {}", err.getMessage()))
                .onErrorResume(err -> Mono.empty());
    }

    public List<String> extractRolesFromToken(String accessToken) {
        List<String> roles = new ArrayList<>();
        try {
            JWT jwt = JWTParser.parse(accessToken);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Map<String, Object> realmAccess = (Map<String, Object>) claims.getClaim("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                List<String> rList = (List<String>) realmAccess.get("roles");
                roles.addAll(rList);
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
