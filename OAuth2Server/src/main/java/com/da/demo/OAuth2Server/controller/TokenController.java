package com.da.demo.OAuth2Server.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.da.demo.OAuth2Server.model.RequestAccessToken;
import com.da.demo.OAuth2Server.model.TokenModel;

@RestController
@RequestMapping("/generateToken/v1")
public class TokenController {
	
	@Value("${server.port}")
	private int portnumber;
	
	@Value("${myapp.client.id}")
	private String clientId;
	
	@Value("${myapp.client.secret}")
	private String clientSecret;
	
	@Value("${myapp.client.redirectUri}")
	private String redirectUri;
	
	@PostMapping(value = "/accessToken", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TokenModel> accessTokenCreator(RequestAccessToken requestAccessToken)
	{
		RestTemplate restTemplate = new RestTemplate();
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.setBasicAuth(clientId, clientSecret);
		
		MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<String, String>();
		requestBody.add("grant_type", "authorization_code");
		requestBody.add("code", requestAccessToken.getCode());
		requestBody.add("redirect_uri", redirectUri);
		requestBody.add("code_verifier", requestAccessToken.getVerifier());
		    
		HttpEntity<MultiValueMap<String, String>> formEntity = new HttpEntity<MultiValueMap<String, String>>(requestBody, headers);
		    
		ResponseEntity<TokenModel> response = 
		   restTemplate.exchange("http://localhost:"+portnumber+"/oauth2/token", HttpMethod.POST, formEntity, TokenModel.class);
		return response;
	}
	
	@PostMapping(value = "/revokeToken")
	public ResponseEntity<Void> accessTokenCreator(@RequestParam String refreshToken)
	{
		RestTemplate restTemplate = new RestTemplate();
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.setBasicAuth(clientId, clientSecret);
		
		MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<String, String>();
		requestBody.add("token", refreshToken);
		requestBody.add("token_type_hint", "refresh_token");
		
		HttpEntity<MultiValueMap<String, String>> formEntity = new HttpEntity<MultiValueMap<String, String>>(requestBody, headers);
		    
		ResponseEntity<Void> response = 
		   restTemplate.exchange("http://localhost:"+portnumber+"/oauth2/revoke", HttpMethod.POST, formEntity, Void.class);
		return response;
	}
}
