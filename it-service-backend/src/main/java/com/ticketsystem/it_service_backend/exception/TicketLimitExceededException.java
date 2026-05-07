package com.ticketsystem.it_service_backend.exception;

public class TicketLimitExceededException extends RuntimeException {

    public TicketLimitExceededException(String message) {
        super(message);
    }
}