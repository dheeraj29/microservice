package com.da.demo.paymentservice.publisher;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.da.demo.paymentservice.model.BookingModel;

@Service
public class RabbitMQProducer {
	@Value("${rabbit.exchange.name}")
	private String exchangename;
	
	@Value("${rabbit.inventory.routekey}")
	private String inventoryRoutekey;
	
	@Value("${rabbit.booking.routekey}")
	private String bookingkey;
	
	private RabbitTemplate rabbitTemplate;
	
	public RabbitMQProducer(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}
	
	public void sendInventoryUpdate(BookingModel bookingModel) {
		rabbitTemplate.convertAndSend(exchangename,inventoryRoutekey,bookingModel);
	}
	
	public void sendBookingUpdate(BookingModel bookingModel) {
		rabbitTemplate.convertAndSend(exchangename,bookingkey,bookingModel);
	}
}
