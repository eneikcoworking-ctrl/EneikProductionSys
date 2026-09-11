package com.eneik.production.controllers.advice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying that client request errors return HTTP 400 Bad Request rather than HTTP 500 Internal Server Error
 * (NUEL_BELNAP_03_TRUTH_STATUS_TABLE, D012 / D006).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Missing request parameter returns 400 BAD_REQUEST instead of 500")
    void missingRequestParameterReturns400() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("projectId", "UUID");

        ResponseEntity<Map<String, Object>> response = handler.handleMissingParameter(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo("Bad Request");
        assertThat(response.getBody().get("message").toString()).contains("projectId");
    }

    @Test
    @DisplayName("Method argument type mismatch returns 400 BAD_REQUEST instead of 500")
    void argumentTypeMismatchReturns400() throws NoSuchMethodException {
        MethodParameter methodParam = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("not-a-uuid", Integer.class, "paramName", methodParam, new IllegalArgumentException());

        ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo("Bad Request");
        assertThat(response.getBody().get("message").toString()).contains("paramName");
    }

    @Test
    @DisplayName("Malformed request body returns 400 BAD_REQUEST instead of 500")
    void malformedRequestBodyReturns400() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error");

        ResponseEntity<Map<String, Object>> response = handler.handleNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo("Bad Request");
    }

    // Helper for reflection parameter in test
    @SuppressWarnings("unused")
    private void dummyMethod(String param) {}
}
