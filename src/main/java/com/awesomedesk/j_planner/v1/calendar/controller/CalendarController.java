package com.awesomedesk.j_planner.v1.calendar.controller;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.common.response.AwesomeResponse;
import com.awesomedesk.j_planner.v1.calendar.domain.Calendar;
import com.awesomedesk.j_planner.v1.calendar.dto.CalenderReqDto;
import com.awesomedesk.j_planner.v1.calendar.service.CalenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("j-planner/v1/calendar")
public class CalendarController {

    private final CalenderService calenderService;

    @GetMapping("test")
    public AwesomeResponse<String> getTest() {
        log.info("CalendarController getTest - input");
        return new AwesomeResponse<>();
    }

    @GetMapping("list")
    public AwesomeResponse<List<Calendar>> getCalenderList(@ModelAttribute DateDto input) {
        log.info("CalendarController getCalenderList - input : {}", input);
        List<Calendar> calenderList = calenderService.getCalenderList(input);
        log.info("CalendarController getCalenderList - calenderList : {}", calenderList);
        return new AwesomeResponse<>(calenderList);
    }

    @PostMapping()
    public AwesomeResponse<Long> postCalender(@PathVariable("calenderId") CalenderReqDto input) {
        log.info("CalendarController getCalenderDetail - input : {}", input);
        Long id = calenderService.postCalender(input);
        return new AwesomeResponse<>(id, "success", "create succsee", HttpStatus.CREATED);
    }

}
