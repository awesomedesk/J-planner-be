package com.awesomedesk.j_planner.v1.calendar.integration;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.v1.calendar.domain.Calendar;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarCreateReqDto;
import com.awesomedesk.j_planner.v1.calendar.repository.CalendarRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Calendar E2E 통합 테스트")
class CalendarIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CalendarRepository calendarRepository;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // 기존 데이터 정리
        calendarRepository.deleteAll();

        // 테스트 데이터 준비
        Calendar calendar1 = createCalendar(
            "팀 회의",
            false,
            LocalDateTime.of(2025, 1, 15, 14, 0),
            LocalDateTime.of(2025, 1, 15, 15, 0),
            "#FF5733"
        );

        Calendar calendar2 = createCalendar(
            "프로젝트 마감",
            true,
            LocalDateTime.of(2025, 1, 20, 0, 0),
            LocalDateTime.of(2025, 1, 20, 23, 59),
            "#3498DB"
        );

        calendarRepository.save(calendar1);
        calendarRepository.save(calendar2);
    }

    @Test
    @DisplayName("E2E: 일정 목록 조회 - 실제 DB 연동")
    void getCalendarList_E2E_Success() throws Exception {
        // When & Then
        mvc.perform(get("/j-planner/v1/calendar/list")
                .param("startDateTime", "2025-01-01T00:00:00")
                .param("endDateTime", "2025-01-31T23:59:59"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.dataCount").value(2))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].title").value("팀 회의"))
            .andExpect(jsonPath("$.data[1].title").value("프로젝트 마감"));
    }

    @Test
    @DisplayName("E2E: 날짜 범위 밖 일정 조회 - 빈 결과")
    void getCalendarList_E2E_EmptyResult() throws Exception {
        // When & Then
        mvc.perform(get("/j-planner/v1/calendar/list")
                .param("startDateTime", "2025-02-01T00:00:00")
                .param("endDateTime", "2025-02-28T23:59:59"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.dataCount").value(0))
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    // TODO: CalendarServiceImpl의 postCalendar 메서드 구현 완료 후 활성화
    // @Test
    @DisplayName("E2E: 일정 저장 후 조회 - 전체 플로우 테스트")
    void postAndGetCalendar_E2E_FullFlow() throws Exception {
        // Given - 일정 생성
        CalendarCreateReqDto reqDto = CalendarCreateReqDto.builder()
            .title("새로운 미팅")
            .describe("클라이언트 미팅")
            .location("강남역")
            .allDay(false)
            .startDatetime(LocalDateTime.of(2025, 1, 25, 16, 0))
            .endDatetime(LocalDateTime.of(2025, 1, 25, 17, 0))
            .build();

        // When - 일정 저장
        mvc.perform(post("/j-planner/v1/calendar/0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").isNumber());

        // Then - 저장된 일정 조회 확인
        mvc.perform(get("/j-planner/v1/calendar/list")
                .param("startDateTime", "2025-01-01T00:00:00")
                .param("endDateTime", "2025-01-31T23:59:59"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataCount").value(3))
            .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    @DisplayName("E2E: DB에 직접 저장 후 API로 조회")
    void saveDirectlyAndRetrieveViaAPI() throws Exception {
        // Given - DB에 직접 저장
        Calendar newCalendar = createCalendar(
            "긴급 회의",
            false,
            LocalDateTime.of(2025, 1, 18, 10, 0),
            LocalDateTime.of(2025, 1, 18, 11, 0),
            "#E74C3C"
        );
        Calendar saved = calendarRepository.save(newCalendar);

        // When & Then - API로 조회
        mvc.perform(get("/j-planner/v1/calendar/list")
                .param("startDateTime", "2025-01-01T00:00:00")
                .param("endDateTime", "2025-01-31T23:59:59"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataCount").value(3))
            .andExpect(jsonPath("$.data[?(@.title == '긴급 회의')]").exists());
    }

    @Test
    @DisplayName("E2E: 삭제된 일정은 조회되지 않음")
    void deletedCalendar_NotRetrieved() throws Exception {
        // Given - 일정을 삭제 상태로 변경
        List<Calendar> calendars = calendarRepository.findAll();
        Calendar calendar = calendars.get(0);
        calendar.setDeleted(true);
        calendar.setDeletedAt(LocalDateTime.now());
        calendarRepository.save(calendar);

        // When & Then - 삭제된 일정은 조회되지 않음
        mvc.perform(get("/j-planner/v1/calendar/list")
                .param("startDateTime", "2025-01-01T00:00:00")
                .param("endDateTime", "2025-01-31T23:59:59"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataCount").value(1))
            .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("E2E: 겹치는 기간의 일정 조회")
    void getCalendarList_OverlappingPeriod() throws Exception {
        // When & Then - 15일 회의 시간과 겹치는 기간으로 조회
        mvc.perform(get("/j-planner/v1/calendar/list")
                .param("startDateTime", "2025-01-15T14:30:00")
                .param("endDateTime", "2025-01-15T14:45:00"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataCount").value(1))
            .andExpect(jsonPath("$.data[0].title").value("팀 회의"));
    }

    // TODO: CalendarServiceImpl의 postCalendar 메서드 구현 완료 후 활성화
    // @Test
    @DisplayName("E2E: 일정 저장 후 DB 검증")
    void postCalendar_VerifyInDatabase() throws Exception {
        // Given
        CalendarCreateReqDto reqDto = CalendarCreateReqDto.builder()
            .title("DB 검증 테스트")
            .describe("테스트 설명")
            .location("테스트 장소")
            .allDay(true)
            .startDatetime(LocalDateTime.of(2025, 1, 30, 0, 0))
            .endDatetime(LocalDateTime.of(2025, 1, 30, 23, 59))
            .build();

        int beforeCount = calendarRepository.findAll().size();

        // When - API로 저장
        mvc.perform(post("/j-planner/v1/calendar/0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
            .andDo(print())
            .andExpect(status().isCreated());

        // Then - DB에서 직접 확인
        int afterCount = calendarRepository.findAll().size();
        assertThat(afterCount).isEqualTo(beforeCount + 1);

        // 실제로 저장된 데이터 확인
        DateDto dateDto = new DateDto(
            LocalDateTime.of(2025, 1, 30, 0, 0),
            LocalDateTime.of(2025, 1, 30, 23, 59)
        );
        List<Calendar> calendars = calendarRepository.findAllByDateDto(dateDto);
        assertThat(calendars).isNotEmpty();
    }

    private Calendar createCalendar(String title, boolean allDay,
                                    LocalDateTime startDateTime, LocalDateTime endDateTime,
                                    String color) {
        try {
            Calendar calendar = Calendar.class.getDeclaredConstructor().newInstance();

            java.lang.reflect.Field titleField = Calendar.class.getDeclaredField("title");
            titleField.setAccessible(true);
            titleField.set(calendar, title);

            java.lang.reflect.Field allDayField = Calendar.class.getDeclaredField("allDay");
            allDayField.setAccessible(true);
            allDayField.set(calendar, allDay);

            java.lang.reflect.Field dateDtoField = Calendar.class.getDeclaredField("dateDto");
            dateDtoField.setAccessible(true);
            dateDtoField.set(calendar, new DateDto(startDateTime, endDateTime));

            java.lang.reflect.Field colorField = Calendar.class.getDeclaredField("color");
            colorField.setAccessible(true);
            colorField.set(calendar, color);

            return calendar;
        } catch (Exception e) {
            throw new RuntimeException("Calendar 생성 실패", e);
        }
    }
}
