package com.da.demo.security.passport;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * OpenFeign Request Interceptor for Internal Passport Propagation.
 * Automatically attaches X-Internal-Passport and X-Passport-User headers
 * containing the caller application name and authenticated user on all Feign calls.
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignPassportRequestInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeignPassportRequestInterceptor.class);

    private final PassportManager passportManager;

    @Value("${spring.application.name:unknown-service}")
    private String appName;

    public FeignPassportRequestInterceptor(PassportManager passportManager) {
        this.passportManager = passportManager;
    }

    @Override
    public void apply(RequestTemplate template) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String username = (auth != null && auth.getName() != null) ? auth.getName() : "system_service";
        List<String> roles = (auth != null && auth.getAuthorities() != null)
                ? auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList())
                : List.of("ROLE_USER");

        try {
            String passportToken = passportManager.mintPassport(appName, username, roles);
            template.header(PassportManager.PASSPORT_HEADER, passportToken);
            template.header(PassportManager.PASSPORT_USER_HEADER, username);
            log.debug("Attached signed X-Internal-Passport from '{}' for user '{}' on Feign call", appName, username);
        } catch (Exception e) {
            log.error("Failed to attach internal passport on Feign call: {}", e.getMessage());
        }
    }
}