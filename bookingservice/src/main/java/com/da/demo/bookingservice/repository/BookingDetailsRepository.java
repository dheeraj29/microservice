package com.da.demo.bookingservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.da.demo.bookingservice.entity.BookingDetails;

@Repository
public interface BookingDetailsRepository extends JpaRepository<BookingDetails, Integer> {
	List<BookingDetails> findByBookingUserOrderByBookingDateDesc(String bookingUser);
	List<BookingDetails> findByBookingUser(String bookingUser);
}
