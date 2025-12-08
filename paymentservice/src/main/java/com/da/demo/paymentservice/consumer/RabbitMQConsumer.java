package com.da.demo.paymentservice.consumer;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.da.demo.paymentservice.model.BookingModel;
import com.da.demo.paymentservice.model.PaymentModel;
import com.da.demo.paymentservice.publisher.RabbitMQProducer;
import com.da.demo.paymentservice.service.PaymentService;

@Service
public class RabbitMQConsumer {
	public static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQConsumer.class);
	
	@Autowired
	PaymentService paymentService;
	
	@Autowired
	RabbitMQProducer rabbitMQProducer;
	
	@RabbitListener(queues= {"${rabbit.payment.queuename}"}, concurrency="1")
	public void receivePayment(BookingModel bookingModel) {
		PaymentModel paymentModel = new PaymentModel();
		paymentModel.setBookingNumber(bookingModel.getBookingNumber());
		paymentModel.setDateOfPayment(LocalDateTime.now());
		PaymentModel paidModel = paymentService.save(paymentModel);
		if(paidModel != null) {
			rabbitMQProducer.sendInventoryUpdate(bookingModel);
		} else {
			bookingModel.setStatus("FAILED");
			rabbitMQProducer.sendBookingUpdate(bookingModel);
			LOGGER.info("Payment failed for {}",bookingModel.getBookingNumber());
		}
	}
}
