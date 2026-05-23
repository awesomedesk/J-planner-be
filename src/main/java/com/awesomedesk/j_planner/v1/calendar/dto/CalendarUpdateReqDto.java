package com.awesomedesk.j_planner.v1.calendar.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CalendarUpdateReqDto {

    private String title;
    private String color;
    private boolean allDay;
    private LocalDateTime startDatetime;
    private LocalDateTime endDatetime;

    private CalendarDetailDto detail;
}
