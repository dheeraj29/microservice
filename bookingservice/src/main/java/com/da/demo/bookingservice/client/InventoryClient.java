package com.da.demo.bookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventoryservice")
public interface InventoryClient {

    @GetMapping("/inventoryservice/v1/getSeatAvailability")
    Integer getSeatAvailability(
            @RequestParam("source") String source,
            @RequestParam("destination") String destination,
            @RequestParam("requiredSeats") Integer requiredSeats
    );
}
