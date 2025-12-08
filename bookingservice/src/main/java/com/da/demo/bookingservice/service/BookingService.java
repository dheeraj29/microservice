package com.da.demo.bookingservice.service;

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
}
