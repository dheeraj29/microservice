package com.da.demo.adminservice;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

import com.da.demo.adminservice.entity.BusDetails;
import com.da.demo.adminservice.repository.BusDetailRepository;

import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.da.demo"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.da.demo"})
public class AdminserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AdminserviceApplication.class, args);
	}

	@Bean
	CommandLineRunner initDatabase(BusDetailRepository repo) {
		return args -> {
			if (repo.count() == 0) {
				BusDetails b1 = new BusDetails();
				b1.setBusNumber(101);
				b1.setSource("New York");
				b1.setDestination("Boston");
				b1.setPrice("45");
				b1.setTotalSeats("40");
				repo.save(b1);

				BusDetails b2 = new BusDetails();
				b2.setBusNumber(204);
				b2.setSource("New York");
				b2.setDestination("Washington DC");
				b2.setPrice("52");
				b2.setTotalSeats("40");
				repo.save(b2);

				BusDetails b3 = new BusDetails();
				b3.setBusNumber(308);
				b3.setSource("Boston");
				b3.setDestination("Philadelphia");
				b3.setPrice("58");
				b3.setTotalSeats("40");
				repo.save(b3);

				BusDetails b4 = new BusDetails();
				b4.setBusNumber(412);
				b4.setSource("Chicago");
				b4.setDestination("Detroit");
				b4.setPrice("38");
				b4.setTotalSeats("40");
				repo.save(b4);
			}
		};
	}
}