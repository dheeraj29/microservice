package com.da.demo.paymentservice.model;

import java.time.LocalDateTime;

public class PaymentModel {
	int paymentNumber;
	int bookingNumber;
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
