package com.da.demo.security.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.da.demo.security.model.SessionRecord;
import com.da.demo.security.session.DistributedSessionManager;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class BffSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BffSessionAuthenticationFilter.class);

    public static final String COOKIE_NAME = "__Host-OmniSession";
    public static final String FALLBACK_COOKIE_NAME = "OmniSession";

    private final DistributedSessionManager sessionManager;

    public BffSessionAuthenticationFilter(DistributedSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. Skip actuator and public auth endpoints
        if (path.startsWith("/actuator/") || path.startsWith("/eureka/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract session ID from cookie
        String sessionId = extractSessionIdFromCookies(request);

        if (sessionId != null && !sessionId.isBlank()) {
            DistributedSessionManager.SessionResolutionResult resolution = sessionManager.resolveSession(sessionId);

            if (resolution != null) {
                SessionRecord session = resolution.getSession();
                String effectiveSid = resolution.getEffectiveSessionId();

                // If arrived on a rotated pointer, update response cookie
                if (resolution.isWasRotated()) {
                    attachSessionCookie(response, effectiveSid);
                }

                // Check token expiration (< 45 seconds remaining)
                if (session.isAccessTokenExpiring(45)) {
                    log.info("Access token for user {} expiring soon. Initiating concurrency-safe rotation...", session.getUsername());
                    try {
                        SessionRecord newSession = sessionManager.refreshAndRotateSession(session);
                        if (newSession != null) {
                            session = newSession;
                            attachSessionCookie(response, newSession.getSessionId());
                        }
                    } catch (Exception e) {
                        log.error("Token rotation failed: {}", e.getMessage());
                    }
                }

                // Authenticate in Spring SecurityContext
                setSecurityContext(session);
                request.setAttribute("X-Authenticated-User", session.getUsername());
            } else {
                // Check for hijack replay on archived session
                if (sessionManager.isRevokedArchive(sessionId)) {
                    log.warn("🚨 STOLEN SESSION REPLAY DETECTED: Expired SID {} presented!", sessionId);
                    sessionManager.handleSessionHijack(sessionId, null);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired or revoked");
                    return;
                }
            }
        } else {
            // 3. Fallback to Authorization: Bearer <jwt> (e.g. Swagger-UI, Feign M2M, External APIs)
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (!token.isBlank()) {
                    try {
                        com.nimbusds.jwt.SignedJWT signedJWT = com.nimbusds.jwt.SignedJWT.parse(token);
                        com.nimbusds.jwt.JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
                        String username = claims.getStringClaim("preferred_username");
                        if (username == null || username.isBlank()) {
                            username = claims.getSubject();
                        }
                        if (username == null || username.isBlank()) {
                            username = "service_account";
                        }

                        List<String> roles = new ArrayList<>();
                        Object realmAccess = claims.getClaim("realm_access");
                        if (realmAccess instanceof java.util.Map) {
                            Object r = ((java.util.Map<?, ?>) realmAccess).get("roles");
                            if (r instanceof List) {
                                for (Object roleObj : (List<?>) r) {
                                    roles.add(roleObj.toString());
                                }
                            }
                        }
                        if (roles.isEmpty()) {
                            roles.add("ROLE_USER");
                        }

                        List<SimpleGrantedAuthority> authorities = roles.stream()
                                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                                .distinct()
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList());

                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(username, token, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        request.setAttribute("X-Authenticated-User", username);
                    } catch (Exception e) {
                        log.debug("Bearer token parse note: {}", e.getMessage());
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractSessionIdFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName()) || FALLBACK_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void setSecurityContext(SessionRecord session) {
        List<SimpleGrantedAuthority> authorities = session.getRoles().stream()
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(session.getUsername(), session.getAccessToken(), authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void attachSessionCookie(HttpServletResponse response, String sessionId) {
        attachSessionCookie(response, sessionId, false);
    }

    public static void attachSessionCookie(HttpServletResponse response, String sessionId, boolean isSecure) {
        if (isSecure) {
            ResponseCookie c1 = ResponseCookie.from(COOKIE_NAME, sessionId)
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(1800)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, c1.toString());
        }
        ResponseCookie c2 = ResponseCookie.from(FALLBACK_COOKIE_NAME, sessionId)
                .httpOnly(true)
                .secure(isSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(1800)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, c2.toString());
    }
}
