package com.da.demo.inventoryservice.controller;

import java.util.Arrays;
import java.util.List;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.da.demo.inventoryservice.model.BusInventoryModel;
import com.da.demo.inventoryservice.service.BusInventoryService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@RestController
@RequestMapping("/inventoryservice/v1")
public class InventoryController {
	
	RestTemplate restTemplate = new RestTemplate();
	
	@Autowired
	private LoadBalancerClient loadBalancerClient;
	
	@Autowired
	BusInventoryService busInventoryService;
	
	@Retry(name = "seatsAvailabilityRetry")
	@CircuitBreaker(name="seatsAvailabilityCB")
	@GetMapping("/getSeatAvailability")
	public Integer getSeatAvailability(@RequestParam(name="source") String source,
			@RequestParam(name="destination") String destination,
			@RequestParam(name="requiredSeats") Integer requiredSeats) {
		ServiceInstance serviceInstance = loadBalancerClient.choose("adminservice");
		String uri = serviceInstance.getUri().toString();
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		HttpEntity<String> entity = new HttpEntity<String>(headers);
		ResponseEntity<List<Integer>> response = restTemplate.exchange(uri+"/adminservice/v1/findBusDetailsBySourceAndDestination?source="+source+"&destination="+destination, HttpMethod.GET, entity, new ParameterizedTypeReference<List<Integer>>() {});
		if(response.getStatusCode().value() == 200) {
			if(response.getBody() != null && !response.getBody().isEmpty()) {
				for(Integer busNumber: response.getBody()) {
					Boolean seatAvailability = busInventoryService.busSeatAvailability(busNumber,requiredSeats);
					if(seatAvailability == Boolean.TRUE) {
						return busNumber;
					}
				}
			}
			return 0;
		} else if(response.getStatusCode().is5xxServerError()) {
			throw new ResponseStatusException(response.getStatusCode());
		} else if(response.getStatusCode().value() == 404) {
			throw new ResponseStatusException(response.getStatusCode());
		} else {
			return null;
		}
	}
	
	@GetMapping("/addBus")
	public String saveBusInventory(@RequestParam(name="busNumber",required=true) Integer busNumber,
			@RequestParam(name="totalSeats",required=true) Integer totalSeats) {
		BusInventoryModel busInventoryModel = busInventoryService.saveDetails(busNumber,totalSeats);
		if(busInventoryModel != null) {
			return "Success";
		} else {
			return "failed";
		}
	}
}
