package com.awesomedesk.j_planner.v1.calendar.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter @Setter
public class CalendarCreateReqDto {

    private String title;
    private String describe;
    private String location;

    private boolean allDay;
    private LocalDateTime startDatetime;
    private LocalDateTime endDatetime;

    public CalendarCreateReqDto() {}

    public CalendarCreateReqDto(String title, String describe, String location, boolean allDay,
        LocalDateTime startDatetime, LocalDateTime endDatetime) {
        this.title = title;
        this.describe = describe;
        this.location = location;
        this.allDay = allDay;
        this.startDatetime = startDatetime;
        this.endDatetime = endDatetime;
    }
}
