package com.awesomedesk.j_planner.common.response.exception.custom;

import com.awesomedesk.j_planner.common.response.exception.AwesomeException;
import org.springframework.http.HttpStatus;

public class InvalidInputException extends AwesomeException {

    public InvalidInputException() {
        super("입력을 확인해주세요", HttpStatus.BAD_REQUEST);
    }

    public InvalidInputException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }

    public InvalidInputException(String message, String logMessage) {
        super(message, logMessage, HttpStatus.BAD_REQUEST);
    }
}
