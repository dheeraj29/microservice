package com.da.demo.paymentservice.publisher;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.da.demo.paymentservice.model.BookingModel;

@Service
public class RabbitMQProducer {
	@Value("${rabbit.exchange.name:bus-exchange}")
	private String exchangename;
	
	@Value("${rabbit.inventory.routekey:inventory-key}")
	private String inventoryRoutekey;
	
	@Value("${rabbit.booking.routekey:booking-key}")
	private String bookingkey;
	
	private RabbitTemplate rabbitTemplate;
	
	public RabbitMQProducer(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}
	
	public void sendInventoryUpdate(BookingModel bookingModel) {
		try {
			if (rabbitTemplate != null) {
				rabbitTemplate.convertAndSend(exchangename, inventoryRoutekey, bookingModel);
			}
		} catch (Exception ignored) {}
	}
	
	public void sendBookingUpdate(BookingModel bookingModel) {
		try {
			if (rabbitTemplate != null) {
				rabbitTemplate.convertAndSend(exchangename, bookingkey, bookingModel);
			}
		} catch (Exception ignored) {}
	}
}
