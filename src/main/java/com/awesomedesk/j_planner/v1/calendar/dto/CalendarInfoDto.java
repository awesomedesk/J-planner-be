package com.awesomedesk.j_planner.v1.calendar.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter @Setter
public class CalendarInfoDto {

    private long id;
    private String title;
    private boolean allDay;
    private LocalDateTime startDatetime;
    private LocalDateTime endDatetime;
    private String color;

    public CalendarInfoDto() {}

    public CalendarInfoDto(long id, String title, boolean allDay,
        LocalDateTime startDatetime, LocalDateTime endDatetime, String color) {
        this.id = id;
        this.title = title;
        this.allDay = allDay;
        this.startDatetime = startDatetime;
        this.endDatetime = endDatetime;
        this.color = color;
    }
}
