package com.da.demo.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = GatewayApplication.class, properties = {"spring.ai.mcp.client.enabled=false"})
class SecurityApplicationTests {

	@Test
	void contextLoads() {
	}

}

