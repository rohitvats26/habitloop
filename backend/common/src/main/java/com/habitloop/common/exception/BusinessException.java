package com.habitloop.common.exception;

import org.springframework.http.HttpStatus;

/** 422 — request is well-formed but violates a business rule. */
public class BusinessException extends ApiException {

    public BusinessException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE", message);
    }

    public BusinessException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
