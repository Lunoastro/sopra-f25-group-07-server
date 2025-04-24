package ch.uzh.ifi.hase.soprafs24.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.http.HttpServletRequest;

// Custom Error Response class
class ErrorResponse {
    private HttpStatus status;
    private String error;
    private String message;

    public ErrorResponse(HttpStatus status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
    }

    // Getters and Setters
    public HttpStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }
}

@ControllerAdvice(annotations = RestController.class)
public class GlobalExceptionAdvice extends ResponseEntityExceptionHandler {

  private final Logger log = LoggerFactory.getLogger(GlobalExceptionAdvice.class);

  // Handle IllegalArgumentException and IllegalStateException
  @ExceptionHandler(value = { IllegalArgumentException.class, IllegalStateException.class })
  protected ResponseEntity<Object> handleConflict(RuntimeException ex, WebRequest request) {
    String bodyOfResponse = ex.getMessage() != null ? ex.getMessage() : "Conflict occurred";
    ErrorResponse errorResponse = new ErrorResponse(HttpStatus.CONFLICT, "Conflict", bodyOfResponse);
    return new ResponseEntity<>(errorResponse, new HttpHeaders(), HttpStatus.CONFLICT);
  }

  // Handle TransactionSystemException
  @ExceptionHandler(TransactionSystemException.class)
  public ResponseEntity<Object> handleTransactionSystemException(Exception ex, HttpServletRequest request) {
    log.error("Request: {} raised {}", request.getRequestURL(), ex);
    ErrorResponse errorResponse = new ErrorResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    return new ResponseEntity<>(errorResponse, new HttpHeaders(), HttpStatus.CONFLICT);
  }

  // Default exception handler for server errors
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleException(Exception ex, HttpServletRequest request) {
    log.error("Default Exception Handler -> caught:", ex);
    ErrorResponse errorResponse = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage());
    return new ResponseEntity<>(errorResponse, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
  }

  // Handle ResponseStatusException
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException ex, WebRequest request) {
    // Log the exception for debugging purposes
    log.error("ResponseStatusException occurred: {}", ex.getMessage());

    // Prepare the error response with status, error type, and message
    ErrorResponse errorResponse = new ErrorResponse(
        ex.getStatus(),
        ex.getReason(), // The reason (e.g., "Bad Request")
        ex.getMessage() // The message from ResponseStatusException
    );

    // Return the custom error response
    return new ResponseEntity<>(errorResponse, new HttpHeaders(), ex.getStatus());
  }
}
