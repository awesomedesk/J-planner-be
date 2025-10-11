package com.awesomedesk.j_planner.v1.calendar.mapper;

import com.awesomedesk.j_planner.v1.calendar.domain.Calendar;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarInfoDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CalendarMapper {

    @Mapping(source = "dateDto.startDateTime", target = "startDatetime")
    @Mapping(source = "dateDto.endDateTime", target = "endDatetime")
    CalendarInfoDto toInfoDto(Calendar calendar);

    List<CalendarInfoDto> toInfoDtoList(List<Calendar> calendars);
}
