package com.da.demo.bookingservice.model;

import java.time.LocalDateTime;

public class BookingModel {

		int bookingNumber;
		int busNumber;
		String bookingUser;
		LocalDateTime bookingDate;
		String source;
		String destination;
		int numberOfSeats;
		String status;
		
		public int getBookingNumber() {
			return bookingNumber;
		}
		public void setBookingNumber(int bookingNumber) {
			this.bookingNumber = bookingNumber;
		}
		public int getBookingId() {
			return bookingNumber;
		}
		public void setBookingId(int bookingId) {
			this.bookingNumber = bookingId;
		}
		public int getBusNumber() {
			return busNumber;
		}
		public void setBusNumber(int busNumber) {
			this.busNumber = busNumber;
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
