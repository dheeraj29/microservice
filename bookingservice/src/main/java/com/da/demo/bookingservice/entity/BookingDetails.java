package com.da.demo.bookingservice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity(name="bookingdetails")
public class BookingDetails {
	
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name="bookingNumber")
	int bookingNumber;
	
	@Column(name="bookingUser")
	String bookingUser;
	
	@Column(name="bookingDate", columnDefinition="TIMESTAMP")
	LocalDateTime bookingDate;
	
	@Column(name="source")
	String source;
	
	@Column(name="destination")
	String destination;
	
	@Column(name="numberOfSeats")
	int numberOfSeats;
	
	@Column(name="status")
	String status;

	public int getBookingNumber() {
		return bookingNumber;
	}

	public void setBookingNumber(int bookingNumber) {
		this.bookingNumber = bookingNumber;
	}

	public String getBookingUser() {
		return bookingUser;
	}

	public void setBookingUser(String bookingUser) {
		this.bookingUser = bookingUser;
	}

	public LocalDateTime getBookingDate() {
		return bookingDate;
	}

	public void setBookingDate(LocalDateTime bookingDate) {
		this.bookingDate = bookingDate;
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

	public int getNumberOfSeats() {
		return numberOfSeats;
	}

	public void setNumberOfSeats(int numberOfSeats) {
		this.numberOfSeats = numberOfSeats;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
