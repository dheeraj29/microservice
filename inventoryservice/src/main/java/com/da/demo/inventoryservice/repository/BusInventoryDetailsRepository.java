package com.da.demo.inventoryservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.da.demo.inventoryservice.entity.BusInventoryDetails;

public interface BusInventoryDetailsRepository extends JpaRepository<BusInventoryDetails,Integer> {
	public BusInventoryDetails findByBusNumber(int busNumber);
}
