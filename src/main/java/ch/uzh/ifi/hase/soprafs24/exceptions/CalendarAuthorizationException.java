package ch.uzh.ifi.hase.soprafs24.exceptions;

public class CalendarAuthorizationException extends RuntimeException {

    public CalendarAuthorizationException(String message) {
        super(message);
    }

    public CalendarAuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
