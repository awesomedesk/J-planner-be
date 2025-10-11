package com.awesomedesk.j_planner.v1.calendar.service;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.v1.calendar.domain.Calendar;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarInfoDto;
import com.awesomedesk.j_planner.v1.calendar.repository.CalendarRepository;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarCreateReqDto;
import com.awesomedesk.j_planner.v1.calendar.mapper.CalendarMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarServiceImpl implements CalendarService {

    private final CalendarRepository calendarRepository;
    private final CalendarMapper calendarMapper;

    @Override
    public List<CalendarInfoDto> getCalendarList(DateDto input) {
        List<Calendar> scheduleList = calendarRepository.findAllByDateDto(input);
        return calendarMapper.toInfoDtoList(scheduleList);
    }

    @Override
    @Transactional()
    public Long postCalendar(CalendarCreateReqDto input) {
        Calendar calendar = new Calendar(); // TODO : input mapper
        Calendar saved = calendarRepository.save(calendar);
        return saved.getId();
    }
}
