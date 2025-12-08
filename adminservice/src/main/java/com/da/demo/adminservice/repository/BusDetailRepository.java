package com.da.demo.adminservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.da.demo.adminservice.entity.BusDetails;

@Repository
public interface BusDetailRepository extends JpaRepository<BusDetails,Integer> {
	public BusDetails findByBusNumber(int busNumber);
	public List<BusDetails> findBySourceAndDestination(String source, String destination);
	public BusDetails deleteByBusNumber(int busNumber);
}
