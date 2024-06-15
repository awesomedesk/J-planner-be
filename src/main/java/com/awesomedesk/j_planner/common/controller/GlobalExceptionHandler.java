package com.awesomedesk.j_planner.common.controller;

import com.awesomedesk.j_planner.common.response.AwesomeResponse;
import com.awesomedesk.j_planner.common.response.exception.AwesomeException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
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
}
