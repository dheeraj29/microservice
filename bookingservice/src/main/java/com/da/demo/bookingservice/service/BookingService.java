package com.da.demo.bookingservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.da.demo.bookingservice.entity.BookingDetails;
import com.da.demo.bookingservice.model.BookingModel;
import com.da.demo.bookingservice.repository.BookingDetailsRepository;

@Service
public class BookingService {
	@Autowired
	BookingDetailsRepository bookingDetailsRepository;
	
	@Autowired
	private ModelMapper modelMapper;
	
	public BookingModel save(BookingModel bookingModel) {
		BookingDetails bookingDetails = modelMapper.map(bookingModel, BookingDetails.class);
		BookingDetails bookedDetails = bookingDetailsRepository.save(bookingDetails);
		BookingModel bookedModel = null;
		if(bookedDetails != null) {
			bookedModel = modelMapper.map(bookedDetails, BookingModel.class);
		}
		return bookedModel;
	}

	public List<BookingModel> findByBookingUser(String bookingUser) {
		List<BookingDetails> detailsList = bookingDetailsRepository.findByBookingUserOrderByBookingDateDesc(bookingUser);
		List<BookingModel> result = new ArrayList<>();
		if (detailsList != null) {
			for (BookingDetails detail : detailsList) {
				result.add(modelMapper.map(detail, BookingModel.class));
			}
		}
		return result;
	}

	public BookingModel findById(Integer bookingId) {
		Optional<BookingDetails> detail = bookingDetailsRepository.findById(bookingId);
		return detail.map(d -> modelMapper.map(d, BookingModel.class)).orElse(null);
	}

	public boolean cancelBooking(Integer bookingId, String requestingUser) {
		Optional<BookingDetails> detailOpt = bookingDetailsRepository.findById(bookingId);
		if (detailOpt.isPresent()) {
			BookingDetails detail = detailOpt.get();
			if (requestingUser == null || requestingUser.equalsIgnoreCase(detail.getBookingUser()) || "admin".equalsIgnoreCase(requestingUser)) {
				detail.setStatus("CANCELLED");
				bookingDetailsRepository.save(detail);
				return true;
			}
		}
		return false;
	}
}
