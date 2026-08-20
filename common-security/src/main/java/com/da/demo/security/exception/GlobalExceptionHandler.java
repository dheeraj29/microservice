package com.da.demo.security.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden: Insufficient Permissions", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized: Authentication Required", ex.getMessage(), request);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(org.springframework.web.bind.MissingServletRequestParameterException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", "Missing required query parameter: " + ex.getParameterName(), request);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid parameter format for: " + ex.getName(), request);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", ex.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(org.springframework.web.servlet.resource.NoResourceFoundException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.web.client.HttpStatusCodeException.class)
    public ResponseEntity<Map<String, Object>> handleHttpStatusCodeException(org.springframework.web.client.HttpStatusCodeException ex, WebRequest request) {
        log.warn("Downstream HTTP error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        return buildErrorResponse(HttpStatus.valueOf(ex.getStatusCode().value()), "Downstream Service Error", ex.getStatusText(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        Map<String, Object> body = createBasePayload(HttpStatus.BAD_REQUEST, "Validation Failed", request);
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unhandled Exception caught by GlobalExceptionHandler: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred. Please try again.", request);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String error, String message, WebRequest request) {
        Map<String, Object> body = createBasePayload(status, error, request);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private Map<String, Object> createBasePayload(HttpStatus status, String error, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        if (request instanceof ServletWebRequest) {
            body.put("path", ((ServletWebRequest) request).getRequest().getRequestURI());
        }
        return body;
    }
}
