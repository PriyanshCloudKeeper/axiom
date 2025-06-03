package com.learnhai.scim.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String SCHEMA_SCIM_ERROR = "urn:ietf:params:scim:api:messages:2.0:Error";

    @ExceptionHandler(ScimException.class)
    public ResponseEntity<Map<String, Object>> handleScimException(ScimException ex, WebRequest request) {
        log.error("SCIM Exception: Status={}, Type={}, Message={}", ex.getStatus(), ex.getScimType(), ex.getMessage(), ex);
        Map<String, Object> errorBody = createScimErrorBody(
                ex.getMessage(),
                ex.getStatus().value(),
                ex.getScimType() != null ? ex.getScimType() : determineScimTypeFromStatus(ex.getStatus())
        );
        return new ResponseEntity<>(errorBody, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        String detail = "Validation failed: " + errors;
        log.warn("Validation Exception: {}", detail, ex);
        Map<String, Object> errorBody = createScimErrorBody(detail, HttpStatus.BAD_REQUEST.value(), "invalidSyntax");
        return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
    }
    
    // Fallback for other RuntimeExceptions
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleGenericRuntimeException(RuntimeException ex, WebRequest request) {
        log.error("Unhandled Runtime Exception: {}", ex.getMessage(), ex);
        Map<String, Object> errorBody = createScimErrorBody(
                "An unexpected internal error occurred.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "internalServerError"
        );
        return new ResponseEntity<>(errorBody, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    // Catch-all for any other exception
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex, WebRequest request) {
        log.error("Generic Exception: {}", ex.getMessage(), ex);
         Map<String, Object> errorBody = createScimErrorBody(
                "An unexpected error occurred: " + ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "internalServerError"
        );
        return new ResponseEntity<>(errorBody, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    private Map<String, Object> createScimErrorBody(String detail, int status, String scimType) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("schemas", Collections.singletonList(SCHEMA_SCIM_ERROR));
        if (scimType != null) {
            error.put("scimType", scimType);
        }
        error.put("detail", detail);
        error.put("status", String.valueOf(status)); // SCIM spec expects status as string
        return error;
    }

    private String determineScimTypeFromStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "invalidSyntax";
            case UNAUTHORIZED -> "invalidCredentials"; // Or based on specific auth failure
            case FORBIDDEN -> "mutability"; // Or noTarget, sensitive
            case NOT_FOUND -> "noTarget";
            case CONFLICT -> "uniqueness"; // Or tooMany
            case INTERNAL_SERVER_ERROR -> "internalServerError";
            default -> null; // Let it be omitted if no clear mapping
        };
    }
}