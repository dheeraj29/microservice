package com.da.demo.serviceregistry.config;

import org.apache.camel.Exchange;
import org.apache.camel.component.seda.ArrayBlockingQueueFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CamelConfigCheck {
	@Bean(name = "Hello")
	ArrayBlockingQueueFactory<Exchange> hello() {
		ArrayBlockingQueueFactory<Exchange> arrayBlockQueue = new ArrayBlockingQueueFactory<>();
		arrayBlockQueue.setFair(true);
		return arrayBlockQueue;
	}
}
