package com.da.demo.OAuth2Server.model;

public class RequestAccessToken {
	String code;
	String verifier;
	
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public String getVerifier() {
		return verifier;
	}
	public void setVerifier(String verifier) {
		this.verifier = verifier;
	}
}
