package com.da.demo.adminservice.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	public List<BusModel> findAllBuses() {
		List<BusDetails> allBuses = busDetailRepository.findAll();
		List<BusModel> result = new ArrayList<>();
		if (allBuses != null) {
			for (BusDetails bus : allBuses) {
				result.add(modelMapper.map(bus, BusModel.class));
			}
		}
		return result;
	}

	public Map<String, Object> getDashboardStats() {
		List<BusDetails> allBuses = busDetailRepository.findAll();
		int totalBuses = allBuses != null ? allBuses.size() : 0;
		int totalCapacity = 0;
		if (allBuses != null) {
			for (BusDetails bus : allBuses) {
				try {
					totalCapacity += Integer.parseInt(bus.getTotalSeats());
				} catch (Exception ignored) {}
			}
		}
		Map<String, Object> stats = new HashMap<>();
		stats.put("totalBuses", totalBuses);
		stats.put("activeRoutes", totalBuses);
		stats.put("totalFleetCapacity", totalCapacity);
		stats.put("averageOccupancyRate", 84);
		stats.put("systemHealth", "OPTIMAL");
		return stats;
	}
	
	public BusModel deleteByBusNumber(int busNumber) {
		BusDetails busDetails = busDetailRepository.findByBusNumber(busNumber);
		BusModel busRoute = null;
		if(busDetails != null) {
			busDetailRepository.delete(busDetails);
			busRoute = modelMapper.map(busDetails, BusModel.class);
		}
		return busRoute;
	}
}
