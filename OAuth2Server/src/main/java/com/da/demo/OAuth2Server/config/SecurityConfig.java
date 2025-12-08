package com.da.demo.OAuth2Server.config;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
	
	@Bean
	@DependsOn({"dataSourceInitializer"})
	RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
		String encodeClientSecret = passwordEncoder.encode("secret");
		JdbcRegisteredClientRepository registeredClientRepository = new JdbcRegisteredClientRepository(jdbcTemplate);
		RegisteredClient registereduser = registeredClientRepository.findByClientId("gateway-client-id");
		String id = null;
		if(registereduser != null) {
			id = registereduser.getId();
		} else {
			id = UUID.randomUUID().toString();
		}
		RegisteredClient registeredClient1 = RegisteredClient.withId(id)
			.clientId("gateway-client-id")
			.clientSecret(encodeClientSecret)
			.clientName("Spring Security Demo")
			.clientAuthenticationMethods(clientTypes -> {
				clientTypes.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
			})
			.authorizationGrantTypes(types -> {
				types.add(AuthorizationGrantType.AUTHORIZATION_CODE);
				types.add(AuthorizationGrantType.REFRESH_TOKEN);
			})
			.redirectUris(redirecturis -> {
				redirecturis.add("https://oauth.pstmn.io/v1/callback");
				redirecturis.add("http://localhost:4200/callback");
				redirecturis.add("http://localhost:8082/oauth2/authorized");
				redirecturis.add("http://localhost:9000/oauth2/authorize");
			})
			.scopes(scopes -> {
				scopes.add(OidcScopes.OPENID);
			})
			.clientSettings(ClientSettings.builder()
					.build())
			.postLogoutRedirectUris(postlogoutredirecturi ->
				postlogoutredirecturi.add("http://localhost:4200/logged-out"))
			.build();
		registeredClientRepository.save(registeredClient1);
		return registeredClientRepository;
	}
	
	@Bean
	DataSourceInitializer dataSourceInitializer(final DataSource datasource) {
		ResourceDatabasePopulator resourceDatabasePopulator = new ResourceDatabasePopulator();
		resourceDatabasePopulator.addScripts(
			new ClassPathResource("/oauth2-authorization-schema-postgres.sql"),
			new ClassPathResource("/oauth2-authorization-consent-schema-postgres.sql"),
			new ClassPathResource("/oauth2-registered-client-schema-postgres.sql"),
			new ClassPathResource("/users-postgres.ddl")
		);
		DataSourceInitializer dataSourceInitializer = new DataSourceInitializer();
		dataSourceInitializer.setDataSource(datasource);
		dataSourceInitializer.setDatabasePopulator(resourceDatabasePopulator);
		return dataSourceInitializer;
	}
	
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
	
	@Bean
	WebSecurityCustomizer webSecurityCustomizer() throws Exception {
		return (web) -> web.ignoring()
				.requestMatchers("/api/v1/createuser")
				.requestMatchers("/generateToken/v1/accessToken");
	}
}
