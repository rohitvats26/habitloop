package com.habitloop.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validationErrorIncludesActionableFieldDetailsInMessage() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must be a valid email address"));
        var method = TestController.class.getDeclaredMethod("endpoint", Object.class);
        var exception = new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        var response = handler.handleValidation(exception, request());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).contains("email: must be a valid email address");
    }

    @Test
    void malformedRequestBodyReturnsBadRequest() {
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("Invalid JSON", (HttpInputMessage) null);

        var response = handler.handleUnreadableBody(exception, request());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST_BODY");
    }

    @Test
    void invalidUuidParameterReturnsHelpfulBadRequest() {
        var exception = new MethodArgumentTypeMismatchException(
                "not-a-uuid", UUID.class, "id", null, new IllegalArgumentException("Invalid UUID"));

        var response = handler.handleTypeMismatch(exception, request());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("id must be a valid UUID.");
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/api/habits");
    }

    private static final class TestController {
        private void endpoint(Object body) {
        }
    }
}
