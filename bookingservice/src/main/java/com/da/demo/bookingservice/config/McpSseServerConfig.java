package com.da.demo.bookingservice.config;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class McpSseServerConfig {

    @Bean
    public HttpServletStreamableServerTransportProvider httpServletStreamableServerTransportProvider(
            JsonMapper mcpServerJsonMapper,
            @Value("${spring.ai.mcp.server.sse.base-url}") String baseUrl) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(mcpServerJsonMapper))
                .mcpEndpoint(baseUrl + "/sse")
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServletRegistrationBean(
            HttpServletStreamableServerTransportProvider httpServletStreamableServerTransportProvider,
            @Value("${spring.ai.mcp.server.sse.base-url}") String baseUrl) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(httpServletStreamableServerTransportProvider, baseUrl + "/*");
        registration.setAsyncSupported(true);
        return registration;
    }
}
