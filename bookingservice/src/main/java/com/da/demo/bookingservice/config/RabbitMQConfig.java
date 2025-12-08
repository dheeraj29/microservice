package com.da.demo.bookingservice.config;

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
	
	@Value("${rabbit.booking.queuename}")
	private String bookqueuename;
	
	@Value("${rabbit.exchange.name}")
	private String exchangename;
	
	@Value("${rabbit.booking.routekey}")
	private String bookroutekey;
	
	@Bean
	Queue bookqueue() {
		return new Queue(bookqueuename,true,false,false);
	}
	
	@Bean
	TopicExchange exchange() {
		return new TopicExchange(exchangename);
	}
	
	@Bean
	Binding bookbinding() {
		return BindingBuilder.bind(bookqueue())
				.to(exchange())
				.with(bookroutekey);
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
