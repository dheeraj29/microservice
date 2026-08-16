package com.da.demo.gateway.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.da.demo.gateway.session.DistributedSessionManager;
import com.da.demo.gateway.session.KeycloakAuthService;
import com.da.demo.gateway.session.SessionRecord;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class BffAuthController {

    private static final Logger log = LoggerFactory.getLogger(BffAuthController.class);

    public static final String COOKIE_NAME = "__Host-OmniSession";
    public static final String FALLBACK_COOKIE_NAME = "OmniSession";

    private final DistributedSessionManager sessionManager;
    private final KeycloakAuthService keycloakAuthService;

    @Value("${keycloak.auth-server-url:http://localhost:8088}")
    private String keycloakUrl;

    @Value("${keycloak.realm:bus-reservation}")
    private String realm;

    @Value("${keycloak.client-id:angular-client}")
    private String clientId;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public BffAuthController(DistributedSessionManager sessionManager,
                             KeycloakAuthService keycloakAuthService) {
        this.sessionManager = sessionManager;
        this.keycloakAuthService = keycloakAuthService;
    }

    private String getExactCallbackUrl() {
        return frontendUrl + "/callback";
    }

    @GetMapping("/login")
    public Mono<Void> initiateLogin(ServerHttpResponse response) {
        String callbackUrl = getExactCallbackUrl();
        String encodedCallback = URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8);
        
        // prompt=login forces Keycloak to always prompt for credentials (no silent auto-login)
        String loginUrl = String.format("%s/realms/%s/protocol/openid-connect/auth?client_id=%s&response_type=code&scope=openid%%20profile%%20email&redirect_uri=%s&prompt=login",
                keycloakUrl, realm, clientId, encodedCallback);

        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(loginUrl));
        return response.setComplete();
    }

    @GetMapping("/callback")
    public Mono<ResponseEntity<Map<String, Object>>> handleCallback(@RequestParam("code") String code,
                                                                     ServerWebExchange exchange) {
        String callbackUrl = getExactCallbackUrl();

        return keycloakAuthService.exchangeAuthorizationCode(code, callbackUrl)
                .flatMap(tokens -> {
                    String fingerprint = exchange.getRequest().getRemoteAddress() != null
                            ? exchange.getRequest().getRemoteAddress().getHostString() : "unknown";
                    return sessionManager.createSession(tokens, fingerprint);
                })
                .flatMap(session -> {
                    boolean isSecure = "https".equalsIgnoreCase(exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto"))
                            || "https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme());
                    attachSessionCookie(exchange.getResponse(), session.getSessionId(), isSecure);

                    boolean isAdmin = session.getRoles().contains("ROLE_ADMIN") || session.getRoles().contains("ADMIN");
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("authenticated", true);
                    resp.put("username", session.getUsername());
                    resp.put("roles", session.getRoles());
                    resp.put("isAdmin", isAdmin);

                    return Mono.just(ResponseEntity.ok(resp));
                })
                .onErrorResume(err -> {
                    log.error("Auth callback failed: {}", err.getMessage());
                    Map<String, Object> errResp = new HashMap<>();
                    errResp.put("authenticated", false);
                    errResp.put("error", err.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errResp));
                });
    }

    @GetMapping("/user")
    public Mono<ResponseEntity<Map<String, Object>>> getCurrentUser(ServerWebExchange exchange) {
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(COOKIE_NAME);
        if (sessionCookie == null) {
            sessionCookie = exchange.getRequest().getCookies().getFirst(FALLBACK_COOKIE_NAME);
        }

        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        return sessionManager.resolveSession(sessionCookie.getValue())
                .map(resolution -> {
                    SessionRecord session = resolution.getSession();
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("authenticated", true);
                    userInfo.put("username", session.getUsername());
                    userInfo.put("roles", session.getRoles());
                    userInfo.put("isAdmin", session.getRoles().contains("ROLE_ADMIN") || session.getRoles().contains("ADMIN"));
                    return ResponseEntity.ok(userInfo);
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/logout")
    public Mono<Void> logout(ServerWebExchange exchange) {
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(COOKIE_NAME);
        if (sessionCookie == null) {
            sessionCookie = exchange.getRequest().getCookies().getFirst(FALLBACK_COOKIE_NAME);
        }

        clearSessionCookie(exchange.getResponse());

        String keycloakLogoutUrl = String.format("%s/realms/%s/protocol/openid-connect/logout?client_id=%s&post_logout_redirect_uri=%s",
                keycloakUrl, realm, clientId, URLEncoder.encode(frontendUrl + "/", StandardCharsets.UTF_8));

        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(keycloakLogoutUrl));

        if (sessionCookie != null) {
            return sessionManager.resolveSession(sessionCookie.getValue())
                    .flatMap(res -> sessionManager.destroySession(res.getSession().getSessionId(), res.getSession().getRefreshToken()))
                    .then(exchange.getResponse().setComplete());
        }

        return exchange.getResponse().setComplete();
    }

    public static void attachSessionCookie(ServerHttpResponse response, String sessionId) {
        attachSessionCookie(response, sessionId, false);
    }

    public static void attachSessionCookie(ServerHttpResponse response, String sessionId, boolean isSecure) {
        if (isSecure) {
            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, sessionId)
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofMinutes(30))
                    .build();
            response.addCookie(cookie);
        }

        ResponseCookie fallback = ResponseCookie.from(FALLBACK_COOKIE_NAME, sessionId)
                .httpOnly(true)
                .secure(isSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(30))
                .build();
        response.addCookie(fallback);
    }

    public static void clearSessionCookie(ServerHttpResponse response) {
        ResponseCookie c1 = ResponseCookie.from(COOKIE_NAME, "").path("/").maxAge(0).build();
        ResponseCookie c2 = ResponseCookie.from(FALLBACK_COOKIE_NAME, "").path("/").maxAge(0).build();
        response.addCookie(c1);
        response.addCookie(c2);
    }
}
