package com.da.demo.inventoryservice;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

import com.da.demo.inventoryservice.entity.BusInventoryDetails;
import com.da.demo.inventoryservice.repository.BusInventoryDetailsRepository;
import java.time.LocalDateTime;

import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.da.demo"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.da.demo"})
public class InventoryserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryserviceApplication.class, args);
	}

	@Bean
	CommandLineRunner initInventory(BusInventoryDetailsRepository repo) {
		return args -> {
			if (repo.count() == 0) {
				int[] buses = { 101, 204, 308, 412 };
				for (int b : buses) {
					BusInventoryDetails inv = new BusInventoryDetails();
					inv.setBusNumber(b);
					inv.setAvailableSeats(40);
					inv.setLastUpdatedDate(LocalDateTime.now());
					repo.save(inv);
				}
			}
		};
	}
}
