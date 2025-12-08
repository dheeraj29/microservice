package com.da.demo.adminservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity(name="busdetails")
public class BusDetails {
	
	@Id
	@Column(name="busNumber")
	int busNumber;

	@Column(name="source",nullable=false)
	String source;
	
	@Column(name="destination",nullable=false)
	String destination;
	
	@Column(name="price",nullable=false)
	String price;
	
	public int getBusNumber() {
		return busNumber;
	}

	public void setBusNumber(int busNumber) {
		this.busNumber = busNumber;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public String getPrice() {
		return price;
	}

	public void setPrice(String price) {
		this.price = price;
	}
}
