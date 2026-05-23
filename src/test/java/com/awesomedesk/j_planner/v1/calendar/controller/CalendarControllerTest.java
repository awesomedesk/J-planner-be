package com.awesomedesk.j_planner.v1.calendar.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarCreateReqDto;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarInfoDto;
import com.awesomedesk.j_planner.v1.calendar.service.CalendarService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Slf4j
@WebMvcTest(
    controllers = CalendarController.class,
    excludeAutoConfiguration = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class}
)
class CalendarControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CalendarService calendarService;

    private ObjectMapper objectMapper;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void getTest() {
    }

    @Test
    @DisplayName("일정 불러오기 테스트")
    void getCalendarList() throws Exception {
        // Given
        List<CalendarInfoDto> mockCalendarList = Arrays.asList(
            CalendarInfoDto.builder()
                .id(1L)
                .title("회의")
                .allDay(false)
                .startDatetime(LocalDateTime.of(2025, 1, 15, 14, 0))
                .endDatetime(LocalDateTime.of(2025, 1, 15, 15, 0))
                .color("#FF5733")
                .build(),
            CalendarInfoDto.builder()
                .id(2L)
                .title("프로젝트 마감")
                .allDay(true)
                .startDatetime(LocalDateTime.of(2025, 1, 20, 0, 0))
                .endDatetime(LocalDateTime.of(2025, 1, 20, 23, 59))
                .color("#3498DB")
                .build()
        );

        given(calendarService.getCalendarList(any(DateDto.class)))
            .willReturn(mockCalendarList);

        // When & Then
        mvc.perform(get("/j-planner/v1/calendar/list")
                .param("startDateTime", "2025-01-01T00:00:00")
                .param("endDateTime", "2025-01-31T23:59:59"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.dataCount").value(2))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[0].title").value("회의"))
            .andExpect(jsonPath("$.data[0].allDay").value(false))
            .andExpect(jsonPath("$.data[0].color").value("#FF5733"))
            .andExpect(jsonPath("$.data[1].id").value(2))
            .andExpect(jsonPath("$.data[1].title").value("프로젝트 마감"))
            .andExpect(jsonPath("$.data[1].allDay").value(true))
            .andExpect(jsonPath("$.data[1].color").value("#3498DB"));
    }

    @Test
    @DisplayName("일정 추가 테스트")
    void postCalendar() throws Exception {
        CalendarCreateReqDto reqDto =
            CalendarCreateReqDto.builder()
                .title("testTitle")
                .color("#FF5733")
                .allDay(false)
                .startDatetime(LocalDateTime.now())
                .endDatetime(LocalDateTime.now().plusHours(3))
                .build();

        given(calendarService.postCalendar(any(CalendarCreateReqDto.class)))
            .willReturn(1L);

        mvc.perform(post("/j-planner/v1/calendar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
            .andExpect(status().isCreated());
    }
}
