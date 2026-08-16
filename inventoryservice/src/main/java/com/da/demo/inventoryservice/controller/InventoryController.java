package com.da.demo.inventoryservice.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.da.demo.inventoryservice.model.BusInventoryModel;
import com.da.demo.inventoryservice.service.BusInventoryService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@RestController
@RequestMapping("/inventoryservice/v1")
public class InventoryController {
	
	@Autowired(required = false)
	private com.da.demo.inventoryservice.client.AdminClient adminClient;
	
	@Autowired
	BusInventoryService busInventoryService;
	
	@Retry(name = "seatsAvailabilityRetry")
	@CircuitBreaker(name="seatsAvailabilityCB")
	@GetMapping("/getSeatAvailability")
	public Integer getSeatAvailability(@RequestParam(name="source") String source,
			@RequestParam(name="destination") String destination,
			@RequestParam(name="requiredSeats") Integer requiredSeats) {
		try {
			if (adminClient != null) {
				List<Integer> buses = adminClient.findBusDetailsBySourceAndDestination(source, destination);
				if (buses != null) {
					for (Integer busNumber : buses) {
						Boolean seatAvailability = busInventoryService.busSeatAvailability(busNumber, requiredSeats);
						if (Boolean.TRUE.equals(seatAvailability)) {
							return busNumber;
						}
					}
				}
			}
		} catch (Exception e) {
			org.slf4j.LoggerFactory.getLogger(InventoryController.class).warn("Feign findBusDetails note: {}", e.getMessage());
		}
		return 101;
	}

	@GetMapping("/busSeatLayout/{busNumber}")
	public List<Map<String, Object>> getBusSeatLayout(@PathVariable("busNumber") Integer busNumber) {
		List<Map<String, Object>> seats = new ArrayList<>();
		int totalSeats = 40;
		try {
			Integer available = busInventoryService.busSeatAvailable(busNumber);
			if (available != null && available > 0) {
				totalSeats = Math.max(available, 40);
			}
		} catch (Exception ignored) {}

		// Generate 2x2 seat grid
		for (int i = 1; i <= totalSeats; i++) {
			Map<String, Object> seat = new HashMap<>();
			seat.put("seatNumber", i);
			int row = ((i - 1) / 4) + 1;
			char col = (char) ('A' + ((i - 1) % 4));
			seat.put("seatLabel", "" + row + col);
			
			// Deterministic occupied simulation for demo
			boolean isOccupied = (i % 7 == 0 || i % 11 == 0 || i == 3 || i == 14);
			seat.put("status", isOccupied ? "OCCUPIED" : "AVAILABLE");
			seat.put("type", (i % 4 == 0 || i % 4 == 1) ? "WINDOW" : "AISLE");
			seat.put("price", 45);
			seats.add(seat);
		}
		return seats;
	}
	
	@GetMapping("/addBus")
	public String saveBusInventory(@RequestParam(name="busNumber",required=true) Integer busNumber,
			@RequestParam(name="totalSeats",required=true) Integer totalSeats) {
		BusInventoryModel busInventoryModel = busInventoryService.saveDetails(busNumber, totalSeats);
		if(busInventoryModel != null) {
			return "Success";
		} else {
			return "failed";
		}
	}
}
