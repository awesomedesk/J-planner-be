package com.awesomedesk.j_planner.v1.calendar.service;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.v1.calendar.domain.Calendar;
import com.awesomedesk.j_planner.v1.calendar.dto.CalenderReqDto;

import java.util.List;

public interface CalenderService {
    List<Calendar> getCalenderList(DateDto input);

    Long postCalender(CalenderReqDto input);
}
