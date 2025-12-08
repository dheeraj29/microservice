package com.da.demo.OAuth2Server.controller;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.da.demo.OAuth2Server.model.Customer;

@Configuration
@RestController
@RequestMapping("/api/v1")
public class CustomerController {
	
	@Autowired
	private DataSource dataSource;
	
	@Autowired
	PasswordEncoder passwordEncoder;
	
	@PostMapping("/createuser")
	public String createUser(@RequestBody Customer customer) {
		UserDetails user = User.withUsername(customer.getUsername())
			.password(passwordEncoder.encode(customer.getPassword()))
			.roles("USER")
			.build();
		JdbcUserDetailsManager users = new JdbcUserDetailsManager(dataSource);
		UserDetails currentuser = null;
		try {
			currentuser = users.loadUserByUsername(customer.getUsername());
		} catch (Exception e) {
		}
		try {
			if(currentuser == null) {
				users.createUser(user);
			} else {
				users.updateUser(user);
			}
			return "User created";
		} catch (Exception e) {
			return "User not created";
		}
	}
}
