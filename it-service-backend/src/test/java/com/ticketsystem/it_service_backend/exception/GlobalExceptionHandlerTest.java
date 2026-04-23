package com.ticketsystem.it_service_backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesResponseStatusException() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.CONFLICT, "duplicate ticket");

        var response = handler.handleResponseStatusException(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().getStatus());
        assertEquals("duplicate ticket", response.getBody().getMessage());
    }

    @Test
    void handlesIllegalArgumentException() {
        IllegalArgumentException exception = new IllegalArgumentException("bad request");

        var response = handler.handleIllegalArgumentException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("bad request", response.getBody().getMessage());
    }

    @Test
    void handlesAccessDeniedException() {
        AccessDeniedException exception = new AccessDeniedException("no access");

        var response = handler.handleAccessDeniedException(exception);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getBody().getStatus());
        assertEquals("Bu işlem için yetkiniz bulunmuyor.", response.getBody().getMessage());
    }

    @Test
    void handlesNoResourceFoundException() {
        NoResourceFoundException exception = new NoResourceFoundException(
            HttpMethod.GET,
            "ticket",
            "/api/tickets/42"
        );

        var response = handler.handleNoResourceFoundException(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
        assertEquals("İstenen kaynak bulunamadı: /api/tickets/42", response.getBody().getMessage());
    }

    @Test
    void handlesGeneralException() {
        Exception exception = new Exception("unexpected failure");

        var response = handler.handleGeneralException(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().getStatus());
        assertEquals("Beklenmedik bir hata oluştu: unexpected failure", response.getBody().getMessage());
    }
}