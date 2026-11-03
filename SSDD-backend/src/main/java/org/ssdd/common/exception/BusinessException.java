package org.ssdd.common.exception;

import org.springframework.http.HttpStatus;

/** Only user-safe, deliberately authored messages may be placed in this exception. */
public class BusinessException extends RuntimeException {
    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        if (!status.isError()) throw new IllegalArgumentException("Business errors require HTTP 4xx/5xx");
        this.status = status;
    }
    public HttpStatus getStatus() { return status; }
}
