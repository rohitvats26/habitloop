package com.habitloop.common.exception;

import org.springframework.http.HttpStatus;

/** 409 — a uniqueness/idempotency conflict (e.g. duplicate completion, existing email). */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE", message);
    }
}
