package com.da.demo.paymentservice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity(name="paymentdetails")
public class PaymentDetails {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name="paymentNumber")
	int paymentNumber;
	
	@Column(name="bookingNumber")
	int bookingNumber;
	
	@Column(name="dateOfPayment", columnDefinition="TIMESTAMP")
	LocalDateTime dateOfPayment;

	public int getPaymentNumber() {
		return paymentNumber;
	}

	public void setPaymentNumber(int paymentNumber) {
		this.paymentNumber = paymentNumber;
	}

	public int getBookingNumber() {
		return bookingNumber;
	}

	public void setBookingNumber(int bookingNumber) {
		this.bookingNumber = bookingNumber;
	}

	public LocalDateTime getDateOfPayment() {
		return dateOfPayment;
	}

	public void setDateOfPayment(LocalDateTime dateOfPayment) {
		this.dateOfPayment = dateOfPayment;
	}
}
