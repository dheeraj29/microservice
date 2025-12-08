package com.da.demo.OAuth2Server.config;

import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;

@Configuration
public class UserConfig {

	
	@Bean
	OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
	    return context -> {
	        if (context.getTokenType() == OAuth2TokenType.ACCESS_TOKEN) {
	            Authentication principal = context.getPrincipal();
	            Set<String> authorities = principal.getAuthorities().stream()
	                    .map(GrantedAuthority::getAuthority)
	                    .collect(Collectors.toSet());
	            context.getClaims().claim("roles", authorities);
	        }
	    };
	}
    
    @Bean
    UserDetailsManager users(DataSource dataSource, PasswordEncoder passwordEncoder) {
        UserDetails user = User.withUsername("admin")
                .password(passwordEncoder.encode("P@ssw0rd"))
                .roles("ADMIN")
                .build();
        JdbcUserDetailsManager users = new JdbcUserDetailsManager(dataSource);
        UserDetails currentuser = null;
        try {
        	currentuser = users.loadUserByUsername("admin");
        } catch (Exception e) {
        }
        if(currentuser == null) {
        	users.createUser(user);
        } else {
        	users.updateUser(user);
        }
        return users;
        
    }
}
