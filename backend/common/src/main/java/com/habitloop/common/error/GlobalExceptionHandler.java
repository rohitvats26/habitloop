package com.habitloop.common.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.habitloop.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

/**
 * Centralized exception handling for all services. Lives in common and is component-scanned
 * by each service, so every controller gets consistent error responses (implementation standard).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("API error at {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.warn("API error [{}] at {}: {}", ex.getCode(), req.getRequestURI(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus()).body(
                ErrorResponse.of(req.getRequestURI(), ex.getStatus().value(), ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .distinct()
                .sorted()
                .toList();
        List<String> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(this::formatObjectError)
                .distinct()
                .sorted()
                .toList();
        List<String> details = java.util.stream.Stream.concat(fieldErrors.stream(), globalErrors.stream()).toList();
        String message = details.isEmpty()
                ? "One or more fields are invalid"
                : "Please correct the invalid fields: " + String.join("; ", details);
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(req.getRequestURI(), 400, "VALIDATION_ERROR", message, details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .toList();
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(req.getRequestURI(), 400, "VALIDATION_ERROR",
                        "Please correct the invalid parameters: " + String.join("; ", details), details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                               HttpServletRequest req) {
        String message = invalidBodyMessage(ex);
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(req.getRequestURI(), 400, "INVALID_REQUEST_BODY", message, null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest req) {
        String expectedType = expectedType(ex.getRequiredType());
        String message = ex.getName() + " must be " + expectedType + ".";
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(req.getRequestURI(), 400, "INVALID_PARAMETER", message, List.of(message)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                  HttpServletRequest req) {
        String message = ex.getParameterName() + " is required.";
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(req.getRequestURI(), 400, "MISSING_PARAMETER", message, List.of(message)));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                   HttpServletRequest req) {
        String message = "HTTP method " + ex.getMethod() + " is not supported for this endpoint.";
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
                ErrorResponse.of(req.getRequestURI(), 405, "METHOD_NOT_ALLOWED", message, null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponse.of(req.getRequestURI(), 401, "UNAUTHORIZED", "Authentication required", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponse.of(req.getRequestURI(), 403, "FORBIDDEN", "Access denied", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error at {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of(req.getRequestURI(), 500, "INTERNAL_ERROR",
                        "An unexpected error occurred", null));
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + (fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
    }

    private String formatObjectError(ObjectError error) {
        return error.getDefaultMessage() == null ? "request: invalid" : error.getDefaultMessage();
    }

    private String expectedType(Class<?> requiredType) {
        if (requiredType == UUID.class) {
            return "a valid UUID";
        }
        if (requiredType == java.time.LocalDate.class) {
            return "a date in yyyy-MM-dd format";
        }
        if (requiredType == java.time.OffsetDateTime.class) {
            return "an ISO-8601 date-time";
        }
        if (requiredType == Integer.class || requiredType == int.class || requiredType == Long.class
                || requiredType == long.class) {
            return "a whole number";
        }
        return requiredType == null ? "a valid value" : "a valid " + requiredType.getSimpleName();
    }

    private String invalidBodyMessage(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof InvalidFormatException invalidFormat) {
            String field = invalidFormat.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(name -> name != null && !name.isBlank())
                    .reduce((parent, child) -> parent + "." + child)
                    .orElse("Request body");
            return field + " has an invalid value.";
        }
        return "Request body is malformed or contains an invalid value.";
    }
}
