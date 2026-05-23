package com.awesomedesk.j_planner.v1.calendar.dto;

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
public class CalendarDetailDto {

    private String description;
    private String textLocation;
    private Double latitude;
    private Double longitude;
}
