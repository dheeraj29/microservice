package com.da.demo.adminservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventoryservice")
public interface InventoryClient {

    @GetMapping("/inventoryservice/v1/addBus")
    String addBus(
            @RequestParam("busNumber") Integer busNumber,
            @RequestParam("totalSeats") String totalSeats
    );
}
