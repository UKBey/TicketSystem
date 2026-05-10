package com.ticketsystem.it_service_backend.exception;

public class TicketLimitExceededException extends RuntimeException {

    private final Object[] messageArgs;

    public TicketLimitExceededException(String messageKey, Object... args) {
        super(messageKey);
        this.messageArgs = args;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
