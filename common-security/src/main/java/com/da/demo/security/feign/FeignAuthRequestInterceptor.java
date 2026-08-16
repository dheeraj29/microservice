package com.da.demo.security.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.da.demo.security.service.M2MTokenService;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class FeignAuthRequestInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeignAuthRequestInterceptor.class);

    private final M2MTokenService m2mTokenService;

    public FeignAuthRequestInterceptor(M2MTokenService m2mTokenService) {
        this.m2mTokenService = m2mTokenService;
    }

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null && attributes.getRequest() != null) {
            // CASE 1: User-Initiated In-Flight Call (Token Relay)
            HttpServletRequest request = attributes.getRequest();

            // 1. Relay Session Cookies
            if (request.getCookies() != null) {
                StringBuilder cookieHeader = new StringBuilder();
                for (Cookie cookie : request.getCookies()) {
                    if ("__Host-OmniSession".equals(cookie.getName()) || "OmniSession".equals(cookie.getName())) {
                        if (cookieHeader.length() > 0) cookieHeader.append("; ");
                        cookieHeader.append(cookie.getName()).append("=").append(cookie.getValue());
                    }
                }
                if (cookieHeader.length() > 0) {
                    template.header("Cookie", cookieHeader.toString());
                }
            }

            // 2. Relay Authorization Header if present
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && !authHeader.isBlank()) {
                template.header("Authorization", authHeader);
            }

            // 3. Relay X-Authenticated-User
            String authUser = request.getHeader("X-Authenticated-User");
            if (authUser != null && !authUser.isBlank()) {
                template.header("X-Authenticated-User", authUser);
            }

            log.debug("Feign Token Relay attached for path: {}", template.url());
        } else {
            // CASE 2: @Scheduled Cron / Async Worker / Background Thread (M2M Service Account)
            log.debug("No active HTTP request context. Acquiring M2M Service Token for Feign call: {}", template.url());
            String m2mToken = m2mTokenService.getInternalM2MToken();
            if (m2mToken != null && !m2mToken.isBlank()) {
                template.header("Authorization", "Bearer " + m2mToken);
                template.header("X-Authenticated-User", "system_scheduler");
            }
        }
    }
}
