package com.da.demo.inventoryservice.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.da.demo.inventoryservice.model.BookingModel;
import com.da.demo.inventoryservice.model.BusInventoryModel;
import com.da.demo.inventoryservice.publisher.RabbitMQProducer;
import com.da.demo.inventoryservice.service.BusInventoryService;

@Service
public class RabbitMQConsumer {
	
	@Autowired
	BusInventoryService busInventoryService;
	
	@Autowired
	RabbitMQProducer rabbitMQProducer;
	
	@RabbitListener(queues = {"${rabbit.inventory.queuename}"}, concurrency="1")
	public void updateInventory(BookingModel bookingModel) {
		Integer currentSeats = busInventoryService.busSeatAvailable(bookingModel.getBusNumber());
		if(currentSeats >= bookingModel.getNumberOfSeats()) {
			currentSeats = currentSeats - bookingModel.getNumberOfSeats();
			BusInventoryModel busInventoryModel = busInventoryService.updateSeatsDetails(bookingModel.getBusNumber(), currentSeats);
			if(busInventoryModel != null) {
				bookingModel.setStatus("SUCCESS");
			} else {
				bookingModel.setStatus("REFUND");
			}
			rabbitMQProducer.sendBookingUpdate(bookingModel);
		} else {
			bookingModel.setStatus("REFUND");
			rabbitMQProducer.sendBookingUpdate(bookingModel);
		}
	}
}
