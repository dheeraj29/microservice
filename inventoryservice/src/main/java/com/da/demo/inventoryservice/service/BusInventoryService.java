package com.da.demo.inventoryservice.service;

import java.time.LocalDateTime;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.da.demo.inventoryservice.entity.BusInventoryDetails;
import com.da.demo.inventoryservice.model.BusInventoryModel;
import com.da.demo.inventoryservice.repository.BusInventoryDetailsRepository;

@Service
public class BusInventoryService {
	@Autowired
	BusInventoryDetailsRepository busInventoryDetailsRepository;
	
	@Autowired
	ModelMapper modelMapper;
	
	public Boolean busSeatAvailability(int busNumber, int requiredSeats) {
		BusInventoryDetails busInventoryDetails = busInventoryDetailsRepository.findByBusNumber(busNumber);
		if(busInventoryDetails != null && busInventoryDetails.getAvailableSeats() >= requiredSeats) {
			return Boolean.TRUE;
		} else {
			return Boolean.FALSE;
		}
	}
	
	public Integer busSeatAvailable(int busNumber) {
		BusInventoryDetails busInventoryDetails = busInventoryDetailsRepository.findByBusNumber(busNumber);
		if(busInventoryDetails != null) {
			return busInventoryDetails.getAvailableSeats();
		} else {
			return 0;
		}
	}
	
	public BusInventoryModel saveDetails(int busNumber, int totalSeats) {
		BusInventoryDetails busInventoryDetails = new BusInventoryDetails();
		BusInventoryModel busInventoryModel = null;
		busInventoryDetails.setBusNumber(busNumber);
		busInventoryDetails.setAvailableSeats(totalSeats);
		busInventoryDetails.setLastUpdatedDate(LocalDateTime.now());
		BusInventoryDetails saveInventoryDetails = busInventoryDetailsRepository.save(busInventoryDetails);
		if(saveInventoryDetails != null) {
			busInventoryModel = modelMapper.map(saveInventoryDetails, BusInventoryModel.class);
		}
		return busInventoryModel;
	}
	
	public BusInventoryModel updateSeatsDetails(int busNumber, int currentSeats) {
		BusInventoryDetails busInventoryDetails = new BusInventoryDetails();
		BusInventoryModel busInventoryModel = null;
		busInventoryDetails.setBusNumber(busNumber);
		busInventoryDetails.setAvailableSeats(currentSeats);
		busInventoryDetails.setLastUpdatedDate(LocalDateTime.now());
		BusInventoryDetails saveInventoryDetails = busInventoryDetailsRepository.save(busInventoryDetails);
		if(saveInventoryDetails != null) {
			busInventoryModel = modelMapper.map(saveInventoryDetails, BusInventoryModel.class);
		}
		return busInventoryModel;
	}
}
