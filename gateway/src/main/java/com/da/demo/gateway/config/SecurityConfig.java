package com.da.demo.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CsrfWebFilter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain apiHttpSecurity(ServerHttpSecurity http) {
        return http
                // CSRF is handled by custom X-Requested-With header check below
                // (SameSite=Lax cookies already block cross-origin POST from iframes/forms)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> {})  // CORS configured via application.yml globalcors
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/auth/**", "/actuator/**", "/eureka/**").permitAll()
                        .anyExchange().permitAll()
                )
                .build();
    }

    /**
     * Custom CSRF protection via X-Requested-With header validation.
     * Browsers never send this header on cross-origin form submissions or link navigations.
     * Combined with SameSite=Lax cookies, this provides defense-in-depth against CSRF.
     * Only enforced on state-changing methods (POST, PUT, DELETE, PATCH).
     */
    @Bean
    WebFilter csrfHeaderFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            String method = exchange.getRequest().getMethod().name();
            String path = exchange.getRequest().getURI().getPath();

            // Skip safe methods and public API/MCP/Keycloak endpoints
            if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)
                    || path.startsWith("/auth") || path.startsWith("/actuator") || path.startsWith("/eureka") || path.startsWith("/mcp") || path.startsWith("/realms")) {
                return chain.filter(exchange);
            }

            // For state-changing requests, require X-Requested-With header
            String xRequestedWith = exchange.getRequest().getHeaders().getFirst("X-Requested-With");
            if (xRequestedWith == null || xRequestedWith.isBlank()) {
                exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            return chain.filter(exchange);
        };
    }
}
