package com.da.demo.bookingservice.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.da.demo.bookingservice.entity.PassengerDetails;
import com.da.demo.bookingservice.model.BookingModel;
import com.da.demo.bookingservice.publisher.RabbitMQProducer;
import com.da.demo.bookingservice.repository.PassengerDetailsRepository;
import com.da.demo.bookingservice.service.BookingService;

@Service
public class RabbitMQConsumer {

	@Autowired
	BookingService bookingService;
	
	@Autowired
	PassengerDetailsRepository passengerDetailsRepository;
	
	@Autowired
	RabbitMQProducer rabbitMQProducer;
	
	@RabbitListener(queues = {"${rabbit.booking.queuename}"}, concurrency="1")
	public void updateBooking(BookingModel bookingModel) {
		BookingModel finalBooked = bookingService.save(bookingModel);
		if(finalBooked != null && finalBooked.getStatus().equalsIgnoreCase("SUCCESS")) {
			int i = bookingModel.getNumberOfSeats();
			while(i >= 0) {
				PassengerDetails passengerDetails = new PassengerDetails();
				passengerDetails.setBookingNumber(bookingModel.getBookingNumber());
				passengerDetailsRepository.save(passengerDetails);
			}
		}
	}
}
