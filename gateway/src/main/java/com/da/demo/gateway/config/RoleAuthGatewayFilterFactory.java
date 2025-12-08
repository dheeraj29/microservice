package com.da.demo.gateway.config;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;

import reactor.core.publisher.Mono;

@Component
public class RoleAuthGatewayFilterFactory extends 
	AbstractGatewayFilterFactory<RoleAuthGatewayFilterFactory.Config> {
		
		Logger logger = LoggerFactory.getLogger(RoleAuthGatewayFilterFactory.class);
		
		public RoleAuthGatewayFilterFactory() {
			super(Config.class);
		}
		
		@Override
		public GatewayFilter apply(Config config) {
			return (exchange, chain) -> {
				ServerHttpRequest request = exchange.getRequest();
				List<String> roles = new ArrayList<>();
				JWTClaimsSet jwtClaimSet = null;
				try {
					JWT jwt = JWTParser.parse(request.getHeaders().get("authorization").toString().replace("Bearer",""));
					jwtClaimSet = jwt.getJWTClaimsSet();
					roles = jwtClaimSet.getStringListClaim("roles");
					logger.debug("role required is {}", config.getRole());
					logger.debug("roles for user available is {}", roles.toString());
				} catch (ParseException e) {
					e.printStackTrace();
				}
				if(roles != null && !roles.isEmpty() && roles.contains("ROLE_"+config.getRole())) {
					final String username = jwtClaimSet.getSubject();
					request.mutate().header("X-Authenticated-User",username);
					return chain.filter(exchange).then(Mono.fromRunnable(() -> {
					}));
				} else {
					var response = exchange.getResponse();
					response.setStatusCode(HttpStatus.UNAUTHORIZED);
					return response.setComplete();
				}
			};
		}
		
		public static class Config {
			private String role;
			
			public String getRole() {
				return role;
			}
			
			public void setRole(String role) {
				this.role = role;
			}
			
			public Config(String role) {
				super();
				this.role = role;
			}
		}
		
		@Override
		public List<String> shortcutFieldOrder() {
			return Arrays.asList("role");
		}
}
