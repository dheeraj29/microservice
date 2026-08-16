package com.da.demo.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiSecurityConfig {

    @Value("${keycloak.auth-server-url:http://localhost:8088}")
    private String keycloakUrl;

    @Value("${keycloak.realm:bus-reservation}")
    private String realm;

    @Bean
    public OpenAPI customOpenAPI() {
        String authUrl = String.format("%s/realms/%s/protocol/openid-connect/auth", keycloakUrl, realm);
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, realm);

        return new OpenAPI()
                .info(new Info()
                        .title("OmniBus Cloud-Native Microservices API")
                        .version("2.0.0")
                        .description("Production-Grade OAuth 2.0 Authorization Code + PKCE & Decentralized Embedded BFF"))
                .addSecurityItem(new SecurityRequirement()
                        .addList("Keycloak_OAuth2")
                        .addList("Bearer_Token"))
                .components(new Components()
                        // 1. Production Standard: OAuth 2.0 Authorization Code Flow with PKCE
                        .addSecuritySchemes("Keycloak_OAuth2", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("Log in with Keycloak (admin/admin123 or john_doe/user123) via Auth Code + PKCE")
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl(authUrl)
                                                .tokenUrl(tokenUrl)
                                                .scopes(new Scopes()
                                                        .addString("openid", "OpenID Connect")
                                                        .addString("profile", "User Profile")
                                                        .addString("roles", "User Roles")))))
                        // 2. Direct Bearer JWT Entry
                        .addSecuritySchemes("Bearer_Token", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Direct JWT Bearer Token (paste accessToken without 'Bearer ')")));
    }
}
