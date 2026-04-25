package com.example.elasticsearchdemo.exception;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler({IOException.class, ElasticsearchException.class})
    public ResponseEntity<Map<String, Object>> handleElasticsearchDown(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ex, request);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, Exception ex, HttpServletRequest request) {
        if (status.is5xxServerError()) {
            log.error("API error {} {} -> {}", request.getMethod(), request.getRequestURI(), status.value(), ex);
        } else {
            log.warn("API error {} {} -> {} ({})", request.getMethod(), request.getRequestURI(), status.value(), ex.getMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", ex.getMessage());
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}

