package com.awesomedesk.j_planner.v1.calendar.controller;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.common.response.AwesomeResponse;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarCreateReqDto;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarInfoDto;
import com.awesomedesk.j_planner.v1.calendar.service.CalendarService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("j-planner/v1/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("list")
    public AwesomeResponse<List<CalendarInfoDto>> getCalendarList(@ModelAttribute DateDto input) {
        log.info("CalendarController getCalendarList - input : {}", input);
        List<CalendarInfoDto> calendarList = calendarService.getCalendarList(input);
        log.info("CalendarController getCalendarList - calendarList : {}", calendarList);
        return new AwesomeResponse<>(calendarList);
    }

    @PostMapping("{calenderId}")
    public AwesomeResponse<Long> postCalendar(@PathVariable("calenderId") Long calenderId,
                                                               @RequestBody CalendarCreateReqDto input) {
        log.info("CalendarController getCalendarDetail - input : {}", input);
        Long id = calendarService.postCalendar(input);
        return new AwesomeResponse<>(id, "success", "create success", HttpStatus.CREATED);
    }

}
