package com.awesomedesk.j_planner.v1.calendar.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CalenderReqDto {

    private String title;
    private String describe;
    private String location;

    private boolean isAllDay;
    private LocalDateTime startDatetime;
    private LocalDateTime endDatetime;
}
