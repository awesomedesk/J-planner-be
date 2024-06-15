package com.awesomedesk.j_planner.common.response.exception.custom;

import com.awesomedesk.j_planner.common.response.exception.AwesomeException;
import org.springframework.http.HttpStatus;

public class EntityNotFoundException extends AwesomeException {

    public EntityNotFoundException() {
        super("결과를 찾을 수 없습니다", HttpStatus.NO_CONTENT);
    }

    public EntityNotFoundException(String message) {
        super(message, HttpStatus.NO_CONTENT);
    }

    public EntityNotFoundException(String message, String logMessage) {
        super(message, logMessage, HttpStatus.NO_CONTENT);
    }
}
