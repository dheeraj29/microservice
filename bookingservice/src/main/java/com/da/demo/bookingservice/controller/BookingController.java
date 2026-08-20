package com.da.demo.bookingservice.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.da.demo.bookingservice.model.BookingModel;
import com.da.demo.bookingservice.publisher.RabbitMQProducer;
import com.da.demo.bookingservice.service.BookingService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@RestController
@RequestMapping("/bookingservice/v1")
public class BookingController {

	@Autowired
	private BookingService bookingService;

	@Autowired(required = false)
	private com.da.demo.bookingservice.client.InventoryClient inventoryClient;
	
	@Autowired(required = false)
	private RabbitMQProducer rabbitMQProducer;
	
	@McpTool(description = "Book passenger bus seats and confirm travel reservation")
	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
	@Retry(name="seatsCheckRetry")
	@CircuitBreaker(name="seatsCheckCB")
	@PostMapping("/bookSeat")
	public ResponseEntity<BookingModel> bookSeat(
			@RequestParam(name="source") String source,
			@RequestParam(name="destination") String destination,
			@RequestParam(name="requiredSeats") Integer requiredSeats,
			@RequestParam(name="bookingUser", required=false) String bookingUser,
			@RequestParam(name="busNumber", required=false) Integer explicitBusNumber,
			@RequestHeader(required=false) HttpHeaders requestHeaders) {
		
		Integer busNumber = explicitBusNumber != null ? explicitBusNumber : 101;
		
		try {
			if (inventoryClient != null) {
				Integer availableBus = inventoryClient.getSeatAvailability(source, destination, requiredSeats);
				if (availableBus != null && availableBus > 0) {
					busNumber = availableBus;
				}
			}
		} catch (Exception e) { org.slf4j.LoggerFactory.getLogger(BookingController.class).warn("Feign inventory check note: {}", e.getMessage()); }

		String resolvedUser = (bookingUser != null && !bookingUser.isBlank()) ? bookingUser : "john_doe";
		if (requestHeaders != null) {
			List<String> userHeaders = requestHeaders.getOrEmpty("X-Authenticated-User");
			if (!userHeaders.isEmpty() && !userHeaders.get(0).isBlank()) {
				resolvedUser = userHeaders.get(0);
			}
		}

		BookingModel bookingModel = new BookingModel();
		bookingModel.setBookingDate(LocalDateTime.now());
		bookingModel.setBookingUser(resolvedUser);
		bookingModel.setSource(source);
		bookingModel.setDestination(destination);
		bookingModel.setNumberOfSeats(requiredSeats);
		bookingModel.setBusNumber(busNumber);
		bookingModel.setStatus("CONFIRMED");

		BookingModel bookedModel = bookingService.save(bookingModel);
		
		try {
			if (rabbitMQProducer != null && bookedModel != null) {
				rabbitMQProducer.sendBooking(bookedModel);
			}
		} catch (Exception e) { org.slf4j.LoggerFactory.getLogger(BookingController.class).error("Failed to publish booking to RabbitMQ saga: {}", e.getMessage()); }

		return ResponseEntity.ok(bookedModel != null ? bookedModel : bookingModel);
	}

	@McpTool(description = "Retrieve list of all active and past bookings for a passenger username")
	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
	@GetMapping("/myBookings")
	public List<BookingModel> getMyBookings(
			@RequestParam(name="username", required=false) String username,
			@RequestHeader(required=false) HttpHeaders requestHeaders) {
		String targetUser = (username != null && !username.isBlank()) ? username : "john_doe";
		if (requestHeaders != null) {
			List<String> userHeaders = requestHeaders.getOrEmpty("X-Authenticated-User");
			if (!userHeaders.isEmpty() && !userHeaders.get(0).isBlank()) {
				targetUser = userHeaders.get(0);
			}
		}
		List<BookingModel> bookings = bookingService.findByBookingUser(targetUser);
		return bookings != null ? bookings : new ArrayList<>();
	}

	@McpTool(description = "Get detailed booking information and ticket status by numeric booking ID")
	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
	@GetMapping("/booking/{id}")
	public ResponseEntity<BookingModel> getBookingById(@PathVariable("id") Integer id) {
		BookingModel model = bookingService.findById(id);
		if (model != null) {
			return ResponseEntity.ok(model);
		}
		return ResponseEntity.notFound().build();
	}

	@McpTool(description = "Cancel an existing passenger booking and release reserved seats")
	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
	@PostMapping("/cancelBooking")
	public ResponseEntity<String> cancelBooking(
			@RequestParam(name="bookingNumber", required=false) Integer bookingNumber,
			@RequestParam(name="bookingId", required=false) Integer bookingId,
			@RequestParam(name="username", required=false) String username) {
		Integer id = (bookingNumber != null) ? bookingNumber : bookingId;
		if (id == null) {
			return ResponseEntity.badRequest().body("Booking ID / Number is required.");
		}
		boolean cancelled = bookingService.cancelBooking(id, username);
		if (cancelled) {
			return ResponseEntity.ok("Booking #" + id + " has been successfully cancelled.");
		}
		return ResponseEntity.badRequest().body("Unable to cancel booking #" + id);
	}
}
