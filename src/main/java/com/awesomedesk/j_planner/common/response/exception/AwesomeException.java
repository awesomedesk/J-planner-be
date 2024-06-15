package com.awesomedesk.j_planner.common.response.exception;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.http.HttpStatus;

@Getter @Setter
@ToString(callSuper = true)
public class AwesomeException extends RuntimeException {

    private HttpStatus status;
    private String logMessage;

    public AwesomeException() {
        setStatusDefault();
    }

    public AwesomeException(String message) {
        super(message);
        setStatusDefault();
    }

    public AwesomeException(String message, Throwable cause) {
        super(message, cause);
        setStatusDefault();
    }

    public AwesomeException(Throwable cause) {
        super(cause);
        setStatusDefault();
    }

    public AwesomeException(String message, Throwable cause, boolean enableSuppression,
        boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        setStatusDefault();
    }

    public AwesomeException(HttpStatus status) {
        this.status = status;
    }

    public AwesomeException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public AwesomeException(String message, Throwable cause, HttpStatus status) {
        super(message, cause);
        this.status = status;
    }

    public AwesomeException(Throwable cause, HttpStatus status) {
        super(cause);
        this.status = status;
    }

    public AwesomeException(String message, Throwable cause, boolean enableSuppression,
        boolean writableStackTrace, HttpStatus status) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.status = status;
    }

    public AwesomeException(String message, String logMessage, HttpStatus status) {
        super(message);
        this.status = status;
        this.logMessage = logMessage;
    }

    public AwesomeException(String message, String logMessage, Throwable cause, HttpStatus status) {
        super(message, cause);
        this.status = status;
        this.logMessage = logMessage;
    }

    public AwesomeException(String message, String logMessage, Throwable cause, boolean enableSuppression,
        boolean writableStackTrace, HttpStatus status) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.status = status;
        this.logMessage = logMessage;
    }

    private void setStatusDefault() {
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

}
