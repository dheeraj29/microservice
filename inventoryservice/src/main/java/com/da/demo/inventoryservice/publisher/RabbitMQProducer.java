package com.da.demo.inventoryservice.publisher;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.da.demo.inventoryservice.model.BookingModel;

@Service
public class RabbitMQProducer {
	
	@Value("${rabbit.exchange.name}")
	private String exchangename;
	
	@Value("${rabbit.booking.routekey}")
	private String routekey;
	
	private RabbitTemplate rabbitTemplate;

	public RabbitMQProducer (RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}
	
	public void sendBookingUpdate(BookingModel bookingModel) {
		rabbitTemplate.convertAndSend(exchangename,routekey,bookingModel);
	}
}
