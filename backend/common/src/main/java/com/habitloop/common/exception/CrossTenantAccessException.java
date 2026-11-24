package com.habitloop.common.exception;

import org.springframework.http.HttpStatus;

/** 403 — an attempt to access another tenant's data. */
public class CrossTenantAccessException extends ApiException {

    public CrossTenantAccessException(String message) {
        super(HttpStatus.FORBIDDEN, "CROSS_TENANT", message);
    }
}
