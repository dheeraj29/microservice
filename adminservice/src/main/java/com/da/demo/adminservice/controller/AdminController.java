package com.da.demo.adminservice.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.da.demo.adminservice.model.BusModel;
import com.da.demo.adminservice.service.BusDetailsService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@RestController
@RequestMapping("/adminservice/v1")
public class AdminController {

	private static final Logger log = LoggerFactory.getLogger(AdminController.class);

	@Autowired
	private BusDetailsService busDetailsService;

	@Autowired(required = false)
	private com.da.demo.adminservice.client.InventoryClient inventoryClient;

	@McpTool(description = "Register and schedule a new bus in the transport fleet")
	@PostMapping("/addBusDetails")
	@PreAuthorize("hasRole('ADMIN')")
	@Retry(name = "addSeatRetry")
	@CircuitBreaker(name = "addSeatCB")
	public ResponseEntity<String> addBusDetails(@RequestBody BusModel busRoute) {
		log.info("Adding bus coach #{}", busRoute.getBusNumber());
		busDetailsService.saveDetails(busRoute);
		try {
			if (inventoryClient != null) {
				inventoryClient.addBus(busRoute.getBusNumber(), busRoute.getTotalSeats());
			}
		} catch (Exception e) {
			log.warn("Feign addBus sync note: {}", e.getMessage());
		}
		return ResponseEntity.status(HttpStatus.CREATED).body("Bus added successfully");
	}

	@McpTool(description = "Retrieve all scheduled buses and routes across the transport fleet")
	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
	@GetMapping("/allBuses")
	public ResponseEntity<List<BusModel>> getAllBuses() {
		return ResponseEntity.ok(busDetailsService.findAllBuses());
	}

	@McpTool(description = "Get executive dashboard statistics including total fleet count, capacity, and system health")
	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
	@GetMapping("/dashboardStats")
	public ResponseEntity<Map<String, Object>> getDashboardStats() {
		return ResponseEntity.ok(busDetailsService.getDashboardStats());
	}

	@McpTool(description = "Find bus details and seat configuration by unique bus number")
	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
	@GetMapping("/findBusDetailsByNumber")
	public ResponseEntity<BusModel> findByDetails(@RequestParam(name = "busNumber", required = true) Integer busNumber) {
		BusModel busRoute = busDetailsService.findByBusNumber(busNumber);
		if (busRoute == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		return ResponseEntity.ok(busRoute);
	}

	@McpTool(description = "Find all bus numbers operating between departure and destination cities")
	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
	@GetMapping("/findBusDetailsBySourceAndDestination")
	public ResponseEntity<List<Integer>> findBusDetailsBySourceAndDestination(
			@RequestParam(name = "source", required = true) String source,
			@RequestParam(name = "destination", required = true) String destination) {
		List<Integer> busNumbers = busDetailsService.findBySourceAndDestination(source, destination);
		return ResponseEntity.ok(busNumbers != null ? busNumbers : new ArrayList<>());
	}

	@PostMapping("/updateBusDetails")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<BusModel> updateBusDetails(
			@RequestParam(name = "busNumber", required = true) Integer busNumber,
			@RequestParam(name = "source", required = false) String source,
			@RequestParam(name = "destination", required = false) String destination,
			@RequestParam(name = "price", required = false) String price) {
		BusModel busRoute = busDetailsService.findByBusNumber(busNumber);
		if (busRoute == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		if (source != null && !source.isBlank()) busRoute.setSource(source);
		if (destination != null && !destination.isBlank()) busRoute.setDestination(destination);
		if (price != null && !price.isBlank()) busRoute.setPrice(price);
		busDetailsService.saveDetails(busRoute);
		return ResponseEntity.ok(busRoute);
	}

	@McpTool(description = "Remove a bus from service by its bus number")
	@DeleteMapping("/deleteBusDetails")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<String> deleteBusDetails(@RequestParam(name = "busNumber", required = true) Integer busNumber) {
		BusModel busRoute = busDetailsService.deleteByBusNumber(busNumber);
		if (busRoute != null) {
			return ResponseEntity.ok("Successfully deleted bus " + busNumber);
		} else {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Bus #" + busNumber + " not found");
		}
	}
}
