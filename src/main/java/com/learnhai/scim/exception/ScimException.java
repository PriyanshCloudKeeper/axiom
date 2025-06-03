package com.learnhai.scim.exception;

import org.springframework.http.HttpStatus;

public class ScimException extends RuntimeException {
    private final HttpStatus status;
    private final String scimType; // SCIM error type e.g., "invalidValue", "uniqueness", "tooMany"

    public ScimException(String message, HttpStatus status) {
        this(message, status, null, null);
    }

    public ScimException(String message, HttpStatus status, String scimType) {
        this(message, status, scimType, null);
    }
    
    public ScimException(String message, HttpStatus status, Throwable cause) {
        this(message, status, null, cause);
    }

    public ScimException(String message, HttpStatus status, String scimType, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.scimType = scimType;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getScimType() {
        return scimType;
    }
}