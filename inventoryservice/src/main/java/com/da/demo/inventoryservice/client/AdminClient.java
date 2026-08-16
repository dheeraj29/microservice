package com.da.demo.inventoryservice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "adminservice")
public interface AdminClient {

    @GetMapping("/adminservice/v1/findBusDetailsBySourceAndDestination")
    List<Integer> findBusDetailsBySourceAndDestination(
            @RequestParam("source") String source,
            @RequestParam("destination") String destination
    );
}
