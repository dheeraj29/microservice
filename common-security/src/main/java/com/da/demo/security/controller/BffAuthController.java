package com.da.demo.security.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.da.demo.security.model.SessionRecord;
import com.da.demo.security.service.KeycloakAuthService;
import com.da.demo.security.service.PkceUtil;
import com.da.demo.security.session.DistributedSessionManager;

/**
 * Decentralized Embedded BFF Authentication Controller.
 * Manages standard OAuth2 / OIDC Authorization Code Flow + PKCE with Keycloak,
 * session creation, and HttpOnly session cookie issuance.
 */
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

    private String sanitizeRedirectUrl(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return null;
        }
        String trimmed = redirect.trim();
        if ("/logout".equals(trimmed) || "/login".equals(trimmed) || "/callback".equals(trimmed) || "/".equals(trimmed)) {
            return null;
        }
        if (trimmed.startsWith("/") && !trimmed.startsWith("//") && !trimmed.contains("\\")) {
            return trimmed;
        }
        log.warn("Rejected potentially unsafe redirect URL: {}", redirect);
        return null;
    }

    /**
     * OIDC Federated SSO Login redirect (Initiates Keycloak Authorization Code Grant with PKCE).
     * Redirects browser to Keycloak's custom themed login page.
     */
    @GetMapping("/login")
    public void initiateLogin(@RequestParam(value = "redirect", required = false) String redirect,
                              HttpServletResponse response) throws IOException {
        String callbackUrl = getExactCallbackUrl();
        String encodedCallback = URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8);

        String state = UUID.randomUUID().toString();
        String codeVerifier = PkceUtil.generateCodeVerifier();
        String codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier);
        String targetUrl = sanitizeRedirectUrl(redirect);

        sessionManager.savePkceState(state, codeVerifier, targetUrl);

        String loginUrl = String.format("%s/realms/%s/protocol/openid-connect/auth?client_id=%s&response_type=code&scope=openid%%20profile%%20email&redirect_uri=%s&prompt=login&code_challenge=%s&code_challenge_method=S256&state=%s",
                keycloakUrl, realm, clientId, encodedCallback, codeChallenge, state);

        response.sendRedirect(loginUrl);
    }

    /**
     * OAuth2 Authorization Code Callback endpoint.
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestParam("code") String code,
                                                              @RequestParam(value = "state", required = false) String state,
                                                              HttpServletRequest request,
                                                              HttpServletResponse response) {
        String callbackUrl = getExactCallbackUrl();

        try {
            DistributedSessionManager.PkceStateRecord pkceState = sessionManager.consumePkceState(state);
            String codeVerifier = pkceState != null ? pkceState.getCodeVerifier() : null;
            String targetUrl = pkceState != null ? pkceState.getTargetUrl() : null;

            Map<String, Object> tokens = keycloakAuthService.exchangeAuthorizationCode(code, callbackUrl, codeVerifier);
            String fingerprint = request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
            SessionRecord session = sessionManager.createSession(tokens, fingerprint);

            boolean isSecure = "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))
                    || "https".equalsIgnoreCase(request.getScheme());
            attachSessionCookie(response, session.getSessionId(), isSecure);

            boolean isAdmin = session.getRoles().contains("ROLE_ADMIN") || session.getRoles().contains("ADMIN");
            Map<String, Object> resp = new HashMap<>();
            resp.put("authenticated", true);
            resp.put("username", session.getUsername());
            resp.put("roles", session.getRoles());
            resp.put("isAdmin", isAdmin);
            resp.put("language", session.getLanguage());
            resp.put("timezone", session.getTimezone());
            resp.put("homepage", session.getHomepage());
            resp.put("theme", session.getTheme());
            if (targetUrl != null && !targetUrl.isBlank()) {
                resp.put("targetUrl", targetUrl);
            }

            return ResponseEntity.ok(resp);
        } catch (Exception err) {
            log.error("Auth callback failed: {}", err.getMessage());
            Map<String, Object> errResp = new HashMap<>();
            errResp.put("authenticated", false);
            errResp.put("error", err.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errResp);
        }
    }

    /**
     * Current authenticated user profile retrieval via HttpOnly cookie session.
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        String sessionId = extractSessionCookie(request);
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        DistributedSessionManager.SessionResolutionResult resolution = sessionManager.resolveSession(sessionId);
        if (resolution == null || resolution.getSession() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SessionRecord session = resolution.getSession();
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("authenticated", true);
        userInfo.put("username", session.getUsername());
        userInfo.put("roles", session.getRoles());
        userInfo.put("isAdmin", session.getRoles().contains("ROLE_ADMIN") || session.getRoles().contains("ADMIN"));
        userInfo.put("language", session.getLanguage());
        userInfo.put("timezone", session.getTimezone());
        userInfo.put("homepage", session.getHomepage());
        userInfo.put("theme", session.getTheme());
        return ResponseEntity.ok(userInfo);
    }

    /**
     * Update user preferences (language, timezone, homepage, theme) in the current session.
     */
    @PutMapping("/user/preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(@RequestBody Map<String, String> preferences,
                                                                 HttpServletRequest request) {
        String sessionId = extractSessionCookie(request);
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SessionRecord updated = sessionManager.updateUserPreferences(sessionId, preferences);
        if (updated == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Persist to Keycloak database using User's Own Bearer Token (Least Privilege)
        keycloakAuthService.updateUserAttributesInKeycloak(updated.getAccessToken(), updated.getUsername(), preferences);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("authenticated", true);
        userInfo.put("username", updated.getUsername());
        userInfo.put("roles", updated.getRoles());
        userInfo.put("isAdmin", updated.getRoles().contains("ROLE_ADMIN") || updated.getRoles().contains("ADMIN"));
        userInfo.put("language", updated.getLanguage());
        userInfo.put("timezone", updated.getTimezone());
        userInfo.put("homepage", updated.getHomepage());
        userInfo.put("theme", updated.getTheme());
        return ResponseEntity.ok(userInfo);
    }

    /**
     * SPA-Friendly POST Logout endpoint: Clears cookies and destroys distributed session.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logoutPost(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = extractSessionCookie(request);
        clearSessionCookie(response);

        Map<String, Object> resp = new HashMap<>();
        resp.put("loggedOut", true);

        if (sessionId != null && !sessionId.isBlank()) {
            try {
                DistributedSessionManager.SessionResolutionResult res = sessionManager.resolveSession(sessionId);
                if (res != null && res.getSession() != null) {
                    sessionManager.destroySession(res.getSession().getSessionId(), res.getSession().getRefreshToken());
                }
            } catch (Exception ignored) {}
        }

        return ResponseEntity.ok(resp);
    }

    /**
     * Browser GET Logout redirect endpoint (Redirects to Keycloak themed logout screen).
     */
    @GetMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionId = extractSessionCookie(request);
        clearSessionCookie(response);

        if (sessionId != null && !sessionId.isBlank()) {
            try {
                DistributedSessionManager.SessionResolutionResult res = sessionManager.resolveSession(sessionId);
                if (res != null && res.getSession() != null) {
                    sessionManager.destroySession(res.getSession().getSessionId(), res.getSession().getRefreshToken());
                }
            } catch (Exception ignored) {}
        }

        String logoutRedirect = frontendUrl + "/logout";
        String keycloakLogoutUrl = String.format("%s/realms/%s/protocol/openid-connect/logout?client_id=%s&post_logout_redirect_uri=%s",
                keycloakUrl, realm, clientId, URLEncoder.encode(logoutRedirect, StandardCharsets.UTF_8));

        response.sendRedirect(keycloakLogoutUrl);
    }

    private String extractSessionCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName()) || FALLBACK_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static void attachSessionCookie(HttpServletResponse response, String sessionId, boolean isSecure) {
        if (isSecure) {
            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, sessionId)
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofMinutes(30))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        ResponseCookie fallback = ResponseCookie.from(FALLBACK_COOKIE_NAME, sessionId)
                .httpOnly(true)
                .secure(isSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(30))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, fallback.toString());
    }

    public static void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie c1 = ResponseCookie.from(COOKIE_NAME, "").path("/").maxAge(0).build();
        ResponseCookie c2 = ResponseCookie.from(FALLBACK_COOKIE_NAME, "").path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, c1.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, c2.toString());
    }
}
