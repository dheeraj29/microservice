package com.da.demo.inventoryservice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity(name="businventorydetails")
public class BusInventoryDetails {
	
	@Id
	@Column(name="busNumber")
	int busNumber;
	
	@Column(name="availableSeats")
	int availableSeats;
	
	@Column(name="lastUpdatedDate", columnDefinition="TIMESTAMP")
	LocalDateTime lastUpdatedDate;

	public int getBusNumber() {
		return busNumber;
	}

	public void setBusNumber(int busNumber) {
		this.busNumber = busNumber;
	}

	public int getAvailableSeats() {
		return availableSeats;
	}

	public void setAvailableSeats(int availableSeats) {
		this.availableSeats = availableSeats;
	}

	public LocalDateTime getLastUpdatedDate() {
		return lastUpdatedDate;
	}

	public void setLastUpdatedDate(LocalDateTime lastUpdatedDate) {
		this.lastUpdatedDate = lastUpdatedDate;
	}
}
