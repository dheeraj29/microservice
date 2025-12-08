package com.da.demo.inventoryservice.config;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
	
	@Value("${rabbit.inventory.queuename")
	private String queuename;
	
	@Value("${rabbit.exchange.name}")
	private String exchangename;
	
	@Value("${rabbit.inventory.routekey}")
	private String routekey;
	
	@Bean
	Queue queue() {
		return new Queue(queuename,true,false,false);
	}
	
	@Bean
	TopicExchange exchange() {
		return new TopicExchange(exchangename);
	}
	
	@Bean
	Binding binding() {
		return BindingBuilder.bind(queue())
				.to(exchange())
				.with(routekey);
	}
	
	//JSON Converter
	@Bean
	MessageConverter converter() {
		return new Jackson2JsonMessageConverter();
	}
	
	@Bean
	AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
		RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
		rabbitTemplate.setMessageConverter(converter());
		return rabbitTemplate;
	}
}
