package com.habitloop.common.exception;

import org.springframework.http.HttpStatus;

/** 401 — authentication missing or invalid. */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
