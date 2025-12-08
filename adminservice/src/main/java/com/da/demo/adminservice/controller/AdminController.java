package com.da.demo.adminservice.controller;

import java.util.ArrayList;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.da.demo.adminservice.model.BusModel;
import com.da.demo.adminservice.service.BusDetailsService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@RestController
@RequestMapping("/adminservice/v1")
public class AdminController {
	
	@Autowired
	BusDetailsService busDetailsService;
	
	RestTemplate restTemplate = new RestTemplate();
	
	@Autowired
	private LoadBalancerClient loadBalancerClient;
	
	@PostMapping("/addBusDetails")
	@Retry(name = "addSeatRetry")
	@CircuitBreaker(name="addSeatCB")
	public String addBusDetails(@RequestBody BusModel busRoute) {
		busDetailsService.saveDetails(busRoute);
		ServiceInstance serviceInstance = loadBalancerClient.choose("inventoryservice");
		String url = serviceInstance.getUri().toString();
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		HttpEntity <String> entity = new HttpEntity<String>(headers);
		ResponseEntity<String> response = restTemplate.exchange(url+"/inventoryservice/v1/addBus?busNumber="+busRoute.getBusNumber()+"&totalSeats="+busRoute.getTotalSeats(), HttpMethod.GET, entity, String.class);
		return response.getBody();
	}
	
	@GetMapping("/findBusDetailsByNumber")
	public BusModel findByDetails(@RequestParam(name="busNumber",required=true) Integer busNumber) {
		BusModel busRoute = new BusModel();
		try {
			busRoute = busDetailsService.findByBusNumber(busNumber);
		} catch (Exception e) {
			
		}
		return busRoute;
	}
	
	@GetMapping("/findBusDetailsBySourceAndDestination")
	public List<Integer> findBusDetailsBySourceAndDestination(@RequestParam(name="source",required=true) String source,
			@RequestParam(name="destination",required=true) String destination) {
		List<Integer> busNumber = new ArrayList<>();
		try {
			busNumber = busDetailsService.findBySourceAndDestination(source,destination);
		} catch (Exception e) {
			
		}
		return busNumber;
	}
	
	@PostMapping("/updateBusDetails")
	public BusModel updateBusDetails(@RequestParam(name="busNumber",required=true) Integer busNumber,
			@RequestParam(name="source",required=true) String source,
			@RequestParam(name="destination",required=true) String destination,
			@RequestParam(name="price",required=true) String price) {
		BusModel busRoute = new BusModel();
		try {
			busRoute = busDetailsService.findByBusNumber(busNumber);
			if(price != null && !price.equalsIgnoreCase("")) {
				busRoute.setPrice(price);
			}
			busDetailsService.saveDetails(busRoute);
		} catch (Exception e) {
			
		}
		return busRoute;
	}
	
	@DeleteMapping("/deleteBusDetails")
	public String deleteBusDetails(@RequestParam(name="busNumber",required=true) Integer busNumber) {
		BusModel busRoute = new BusModel();
		try {
			busRoute = busDetailsService.deleteByBusNumber(busNumber);
		} catch (Exception e) {
			
		}
		if(busRoute != null) {
			return "Successfully delete bus";
		} else {
			return "Bus not present or some issue in deleting";
		}
	}
}
