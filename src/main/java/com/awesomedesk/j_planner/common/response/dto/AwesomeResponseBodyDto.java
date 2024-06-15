package com.awesomedesk.j_planner.common.response.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.ObjectUtils;

@Getter @Setter
@ToString
@NoArgsConstructor
public class AwesomeResponseBodyDto<T> {

    T data;
    int dataCount;
    String message;
    String logMessage;

    public AwesomeResponseBodyDto(T data) {
        this.data = data;

        if (ObjectUtils.isEmpty(data)) {
            this.dataCount = 0;
        } else if (data instanceof List<?>) {
            this.dataCount = ((List<?>) data).size();
        } else if (data instanceof Object[]) {
            this.dataCount = ((Object[]) data).length;
        } else {
            this.dataCount = 1;
        }
    }

    public AwesomeResponseBodyDto(T data, String message, String logMessage) {
        this(data);
        this.message = message;
        this.logMessage = logMessage;
    }

}
