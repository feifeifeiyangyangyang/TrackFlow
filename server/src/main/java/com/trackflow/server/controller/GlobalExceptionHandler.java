package com.trackflow.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(this::fieldMessage)
        .collect(Collectors.joining("; "));
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
  }

  @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
  public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", cleanMessage(ex), request);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    return error(status, codeFor(status), ex.getReason() == null ? status.getReasonPhrase() : ex.getReason(), request);
  }

  @ExceptionHandler(EmptyResultDataAccessException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(EmptyResultDataAccessException ex, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found", request);
  }

  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<ErrorResponse> handleConflict(DuplicateKeyException ex, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "Duplicate business key", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error", request);
  }

  private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status).body(new ErrorResponse(code, message, Instant.now(), request.getRequestURI(), requestId()));
  }

  private String fieldMessage(FieldError error) {
    return error.getField() + " " + (error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
  }

  private String cleanMessage(Exception ex) {
    String message = ex.getMessage();
    return message == null || message.isBlank() ? "Bad request" : message;
  }

  private String requestId() {
    String traceId = MDC.get("traceId");
    return traceId == null || traceId.isBlank() ? "unknown" : traceId;
  }

  private String codeFor(HttpStatus status) {
    return switch (status) {
      case BAD_REQUEST -> "BAD_REQUEST";
      case UNAUTHORIZED -> "INVALID_SIGNATURE";
      case NOT_FOUND -> "RESOURCE_NOT_FOUND";
      case CONFLICT -> "STATE_CONFLICT";
      case BAD_GATEWAY -> "CARRIER_ERROR";
      case GATEWAY_TIMEOUT -> "CARRIER_TIMEOUT";
      case TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS";
      default -> "ERROR";
    };
  }
}
