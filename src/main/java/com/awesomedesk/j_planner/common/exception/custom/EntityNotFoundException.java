package com.awesomedesk.j_planner.common.exception.custom;

import com.awesomedesk.j_planner.common.exception.AwesomeException;
import org.springframework.http.HttpStatus;

public class EntityNotFoundException extends AwesomeException {

    public EntityNotFoundException() {
        super("결과를 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }

    public EntityNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }

    public EntityNotFoundException(String message, String logMessage) {
        super(message, logMessage, HttpStatus.NOT_FOUND);
    }
}
