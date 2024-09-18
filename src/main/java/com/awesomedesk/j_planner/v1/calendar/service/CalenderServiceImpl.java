package com.awesomedesk.j_planner.v1.calendar.service;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.v1.calendar.domain.Calendar;
import com.awesomedesk.j_planner.v1.calendar.repository.CalenderRepository;
import com.awesomedesk.j_planner.v1.calendar.dto.CalenderReqDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CalenderServiceImpl implements CalenderService {

    private final CalenderRepository calenderRepository;

    @Override
    public List<Calendar> getCalenderList(DateDto input) {
        return calenderRepository.findAllByStartDateTimeAndEndDateTime(input.getStartDateTime(), input.getEndDateTime());
    }

    @Override
    @Transactional()
    public Long postCalender(CalenderReqDto input) {
        Calendar calendar = new Calendar(); // TODO : input mapper
        Calendar saved = calenderRepository.save(calendar);
        return saved.getId();
    }
    // pr test
}
