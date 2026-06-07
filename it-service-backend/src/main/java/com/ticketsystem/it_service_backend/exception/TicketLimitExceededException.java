package com.ticketsystem.it_service_backend.exception;

/**
 * Thrown when the active ticket capacity per agent / product is exceeded.
 *
 * <p>The message carries a {@code MessageSource} key; its placeholder
 * arguments are exposed via {@link #getMessageArgs()}.
 * {@link GlobalExceptionHandler} translates this exception to HTTP
 * {@code 409 Conflict}.
 */
public class TicketLimitExceededException extends RuntimeException {

    private final transient Object[] messageArgs;

    /**
     * @param messageKey i18n bundle key (looked up in
     *                   {@code messages.properties})
     * @param args       values for the {@code {0}}, {@code {1}}... placeholders
     *                   in the message
     */
    public TicketLimitExceededException(String messageKey, Object... args) {
        super(messageKey);
        this.messageArgs = args;
    }

    /**
     * @return placeholder arguments to be forwarded to
     *         {@code MessageSource.getMessage}
     */
    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
