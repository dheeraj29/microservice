package com.da.demo.paymentservice.service;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.da.demo.paymentservice.entity.PaymentDetails;
import com.da.demo.paymentservice.model.PaymentModel;
import com.da.demo.paymentservice.repository.PaymentDetailsRepository;

@Service
public class PaymentService {
	@Autowired
	PaymentDetailsRepository paymentDetailsRepository;
	
	@Autowired
	ModelMapper modelMapper;
	
	public PaymentModel save(PaymentModel paymentModel) {
		PaymentModel payDetailsModel = null;
		PaymentDetails paymentDetails = modelMapper.map(paymentModel, PaymentDetails.class);
		PaymentDetails payDetails = paymentDetailsRepository.save(paymentDetails);
		if(payDetails != null) {
			payDetailsModel = modelMapper.map(payDetails, PaymentModel.class);
		}
		return payDetailsModel;
	}
}
