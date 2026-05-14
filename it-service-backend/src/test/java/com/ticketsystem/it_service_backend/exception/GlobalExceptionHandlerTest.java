package com.ticketsystem.it_service_backend.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private MessageSource messageSource;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        // MessageSource'u mock'layarak handler'ı bağımsız test edebiliriz.
        // useCodeAsDefaultMessage=true davranışını simüle etmek için key'i olduğu gibi döndürüyoruz.
        messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        handler = new GlobalExceptionHandler(messageSource);
    }

    @Test
    void handlesResponseStatusException() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.CONFLICT, "duplicate ticket");

        var response = handler.handleResponseStatusException(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().getStatus());
        // reason bir message key olarak işlenir; mock key'i olduğu gibi döndürür
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
        // error.access.denied key'i için Türkçe mesaj döndür
        when(messageSource.getMessage(eq("error.access.denied"), any(), any(Locale.class)))
                .thenReturn("Bu işlem için yetkiniz bulunmuyor.");

        AccessDeniedException exception = new AccessDeniedException("no access");

        var response = handler.handleAccessDeniedException(exception);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getBody().getStatus());
        assertEquals("Bu işlem için yetkiniz bulunmuyor.", response.getBody().getMessage());
    }

    @Test
    void handlesNoResourceFoundException() {
        // error.resource.not.found key'i için çeviri simüle et
        when(messageSource.getMessage(eq("error.resource.not.found"), any(), any(Locale.class)))
                .thenReturn("İstenen kaynak bulunamadı: /api/tickets/42");

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
        // error.unexpected key'i için çeviri simüle et
        when(messageSource.getMessage(eq("error.unexpected"), any(), any(Locale.class)))
                .thenReturn("Beklenmedik bir hata oluştu: unexpected failure");

        Exception exception = new Exception("unexpected failure");

        var response = handler.handleGeneralException(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().getStatus());
        assertEquals("Beklenmedik bir hata oluştu: unexpected failure", response.getBody().getMessage());
    }

    @Test
    void handlesResponseStatusException_withNullReason() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST);

        var response = handler.handleResponseStatusException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("", response.getBody().getMessage());
    }

    @Test
    void handlesIllegalArgumentException_withNullMessage() {
        when(messageSource.getMessage(eq("error.unexpected"), any(), any(Locale.class)))
                .thenReturn("default error");

        IllegalArgumentException exception = new IllegalArgumentException();

        var response = handler.handleIllegalArgumentException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("default error", response.getBody().getMessage());
    }

    @Test
    void handlesUserAlreadyExistsException_email() {
        when(messageSource.getMessage(eq("error.user.already.exists"), any(), any(Locale.class)))
                .thenReturn("user exists");
        when(messageSource.getMessage(eq("error.user.already.exists.email"), any(), any(Locale.class)))
                .thenReturn("email already used");

        UserAlreadyExistsException exception = new UserAlreadyExistsException("email", "x@y");

        var response = handler.handleUserAlreadyExistsException(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("USER_ALREADY_EXISTS", response.getBody().getError());
        assertEquals("user exists", response.getBody().getMessage());
        assertTrue(response.getBody().getFieldErrors().containsKey("email"));
    }

    @Test
    void handlesUserAlreadyExistsException_username() {
        when(messageSource.getMessage(eq("error.user.already.exists"), any(), any(Locale.class)))
                .thenReturn("user exists");
        when(messageSource.getMessage(eq("error.user.already.exists.username"), any(), any(Locale.class)))
                .thenReturn("username taken");

        UserAlreadyExistsException exception = new UserAlreadyExistsException("username", "john");

        var response = handler.handleUserAlreadyExistsException(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().getFieldErrors().containsKey("username"));
    }

    @Test
    void handlesTicketLimitExceededException() {
        when(messageSource.getMessage(eq("error.limit.exceeded"), any(), any(Locale.class)))
                .thenReturn("limit reached");

        TicketLimitExceededException exception =
                new TicketLimitExceededException("error.limit.exceeded", 5);

        var response = handler.handleTicketLimitExceededException(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("TICKET_LIMIT_EXCEEDED", response.getBody().getError());
        assertEquals("limit reached", response.getBody().getMessage());
    }

    @Test
    void handlesMethodArgumentNotValidException() {
        // error.validation.failed key'i için çeviri simüle et
        when(messageSource.getMessage(eq("error.validation.failed"), any(), any(Locale.class)))
                .thenReturn("Doğrulama hatası");

        FieldError fieldError = new FieldError("ticketDTO", "title", "Başlık boş olamaz");
        when(messageSource.getMessage(any(FieldError.class), any(Locale.class)))
                .thenReturn("Başlık boş olamaz");

        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        var response = handler.handleMethodArgumentNotValidException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("Doğrulama hatası", response.getBody().getMessage());
        assertNotNull(response.getBody().getFieldErrors());
        assertTrue(response.getBody().getFieldErrors().containsKey("title"));
        assertEquals("Başlık boş olamaz", response.getBody().getFieldErrors().get("title"));
    }
}
