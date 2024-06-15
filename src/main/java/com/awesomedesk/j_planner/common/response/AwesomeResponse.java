package com.awesomedesk.j_planner.common.response;

import com.awesomedesk.j_planner.common.response.dto.AwesomeResponseBodyDto;
import lombok.ToString;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

@ToString(callSuper = true)
public class AwesomeResponse<T> extends ResponseEntity<AwesomeResponseBodyDto<T>> {

    public AwesomeResponse() {
        super(new AwesomeResponseBodyDto<>(), HttpStatus.OK);
    }

    public AwesomeResponse(HttpStatusCode status) {
        super(new AwesomeResponseBodyDto<>(), status);
    }

    public AwesomeResponse(AwesomeResponseBodyDto<T> body, HttpStatusCode status) {
        super(body, status);
    }

    public AwesomeResponse(T data) {
        super(new AwesomeResponseBodyDto<>(data),
            HttpStatus.OK);
    }

    public AwesomeResponse(T data, String message) {
        super(new AwesomeResponseBodyDto<>(data, message, null),
            HttpStatus.OK);
    }

    public AwesomeResponse(T data, String message, String logMessage) {
        super(new AwesomeResponseBodyDto<>(data, message, logMessage),
            HttpStatus.OK);
    }

    public AwesomeResponse(T data, String message, String logMessage, HttpStatusCode status) {
        super(new AwesomeResponseBodyDto<>(data, message, logMessage),
            status);
    }

}
