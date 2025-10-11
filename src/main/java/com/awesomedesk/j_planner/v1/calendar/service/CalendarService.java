package com.awesomedesk.j_planner.v1.calendar.service;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarCreateReqDto;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarInfoDto;
import java.util.List;

public interface CalendarService {
    List<CalendarInfoDto> getCalendarList(DateDto input);

    Long postCalendar(CalendarCreateReqDto input);
}
