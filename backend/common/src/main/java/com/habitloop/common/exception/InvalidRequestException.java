package com.habitloop.common.exception;

import org.springframework.http.HttpStatus;

/** 400 — request data is malformed or outside the supported input bounds. */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
