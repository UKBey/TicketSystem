package com.ticketsystem.it_service_backend.exception;

/**
 * Agent / urun bazli aktif bilet kapasitesi asildiginda firlatilir.
 *
 * <p>Mesaj olarak bir {@code MessageSource} anahtari tasinir; bunun
 * placeholder argumanlari {@link #getMessageArgs()} ile saglanir.
 * {@link GlobalExceptionHandler} bu exception'i HTTP {@code 409 Conflict}'e
 * cevirir.
 */
public class TicketLimitExceededException extends RuntimeException {

    private final Object[] messageArgs;

    /**
     * @param messageKey i18n bundle anahtari ({@code messages.properties}'te aranir)
     * @param args       mesajdaki {@code {0}}, {@code {1}}... yer tutuculari icin degerler
     */
    public TicketLimitExceededException(String messageKey, Object... args) {
        super(messageKey);
        this.messageArgs = args;
    }

    /**
     * @return {@code MessageSource.getMessage} cagrisina aktarilacak yer-tutucu argumanlari
     */
    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
