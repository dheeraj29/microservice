package com.da.demo.adminservice.service;

import java.util.ArrayList;
import java.util.List;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.da.demo.adminservice.entity.BusDetails;
import com.da.demo.adminservice.model.BusModel;
import com.da.demo.adminservice.repository.BusDetailRepository;

@Service
public class BusDetailsService {
	
	@Autowired
	BusDetailRepository busDetailRepository;
	
	@Autowired
	private ModelMapper modelMapper;
	
	public void saveDetails(BusModel busRoute) {
		BusDetails busDetails = modelMapper.map(busRoute, BusDetails.class);
		busDetailRepository.save(busDetails);
	}
	
	public BusModel findByBusNumber(int busNumber) {
		BusDetails busDetails = busDetailRepository.findByBusNumber(busNumber);
		BusModel busRoute = null;
		if(busDetails != null) {
			busRoute = modelMapper.map(busDetails, BusModel.class);
		}
		return busRoute;
	}
	
	public List<Integer> findBySourceAndDestination(String source, String destination) {
		List<BusDetails> busDetails = busDetailRepository.findBySourceAndDestination(source, destination);
		List<Integer> busNumbers = new ArrayList<>();
		if(busDetails != null) {
			for(BusDetails busDetail: busDetails) {
				busNumbers.add(busDetail.getBusNumber());
			}
		}
		return busNumbers;
	}
	
	public BusModel deleteByBusNumber(int busNumber) {
		BusDetails busDetails = busDetailRepository.deleteByBusNumber(busNumber);
		BusModel busRoute = null;
		if(busDetails != null) {
			busRoute = modelMapper.map(busDetails, BusModel.class);
		}
		return busRoute;
	}
}
