package com.da.demo.bookingservice.controller;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.da.demo.bookingservice.model.BookingModel;
import com.da.demo.bookingservice.publisher.RabbitMQProducer;
import com.da.demo.bookingservice.service.BookingService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@RestController
@RequestMapping("/bookingservice/v1")
public class BookingController {

	RestTemplate restTemplate = new RestTemplate();
	
	@Autowired
	private BookingService bookingService;
	
	@Autowired
	private LoadBalancerClient loadBalancerClient;
	
	@Autowired
	private RabbitMQProducer rabbitMQProducer;
	
	@Retry(name="seatsCheckRetry")
	@CircuitBreaker(name="seatsCheckCB")
	@PostMapping("/bookSeat")
	public String bookSeat(@RequestParam(name="source") String source,
			@RequestParam(name="destination") String destination,
			@RequestParam(name="requiredSeats") Integer requiredSeats,
			@RequestHeader HttpHeaders requestHeaders) {
		ServiceInstance serviceInstance = loadBalancerClient.choose("inventoryservice");
		String uri = serviceInstance.getUri().toString();
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		HttpEntity<String> entity = new HttpEntity<String>(headers);
		ResponseEntity<Integer> response = restTemplate.exchange(uri+"/inventoryservice/v1/getSeatAvailability?source="+source+"&destination="+destination+"&requiredSeats="+requiredSeats, HttpMethod.GET, entity, Integer.class);
		if(response.getBody() != null && response.getStatusCode().value() == 200) {
			if(response.getBody() > 0) {
				BookingModel bookingModel = new BookingModel();
				bookingModel.setBookingDate(LocalDateTime.now());
				List<String> users = requestHeaders.getOrEmpty("X-Authenticated-User");
				if(!users.isEmpty()) {
					bookingModel.setBookingUser(users.get(0));
				}
				bookingModel.setSource(source);
				bookingModel.setDestination(destination);
				bookingModel.setNumberOfSeats(requiredSeats);
				bookingModel.setStatus("PENDING");
				BookingModel bookedModel = bookingService.save(bookingModel);
				if(bookedModel != null) {
					bookedModel.setBusNumber(response.getBody());
					rabbitMQProducer.sendBooking(bookedModel);
					return "Seat available, booking inprogress";
				} else {
					return "Failed to book seat";
				}
			} else {
				return "Seat not available";
			}
		} else {
			throw new ResponseStatusException(response.getStatusCode());
		}
	}
}
