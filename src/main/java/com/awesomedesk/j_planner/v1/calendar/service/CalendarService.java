package com.awesomedesk.j_planner.v1.calendar.service;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarCreateReqDto;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarInfoDto;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarUpdateReqDto;
import java.util.List;

public interface CalendarService {
    List<CalendarInfoDto> getCalendarList(DateDto input);

    Long postCalendar(CalendarCreateReqDto input);

    Long updateCalendar(Long calenderId, CalendarUpdateReqDto input);

    void deleteCalendar(Long calenderId);
}
