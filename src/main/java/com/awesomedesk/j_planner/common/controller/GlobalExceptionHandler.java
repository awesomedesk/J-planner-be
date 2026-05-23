package com.awesomedesk.j_planner.common.controller;

import com.awesomedesk.j_planner.common.exception.AwesomeException;
import com.awesomedesk.j_planner.common.exception.custom.EntityNotFoundException;
import com.awesomedesk.j_planner.common.response.AwesomeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
class GlobalExceptionHandler {

    @ExceptionHandler(AwesomeException.class)
    public AwesomeResponse<AwesomeException> handleCustomException(AwesomeException ex) {
        return new AwesomeResponse<>(
            ex,
            ex.getMessage(),
            ex.getLogMessage(),
            ex.getStatus()
        );
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public AwesomeResponse<EntityNotFoundException> handleJpaEntityNotFoundException(
        jakarta.persistence.EntityNotFoundException ex
    ) {
        EntityNotFoundException wrapped = new EntityNotFoundException(ex.getMessage());
        return new AwesomeResponse<>(
            wrapped,
            wrapped.getMessage(),
            wrapped.getLogMessage(),
            wrapped.getStatus()
        );
    }

    @ExceptionHandler(Exception.class)
    public AwesomeResponse<Void> handleUnhandledException(Exception ex) {
        log.error("Unhandled exception", ex);
        return new AwesomeResponse<>(
            null,
            "internal server error",
            ex.getClass().getName(),
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
