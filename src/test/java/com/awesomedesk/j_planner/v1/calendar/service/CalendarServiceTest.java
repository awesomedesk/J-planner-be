package com.awesomedesk.j_planner.v1.calendar.service;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.v1.calendar.domain.Calendar;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarCreateReqDto;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarInfoDto;
import com.awesomedesk.j_planner.v1.calendar.mapper.CalendarMapper;
import com.awesomedesk.j_planner.v1.calendar.repository.CalendarRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("CalendarService 테스트")
class CalendarServiceTest {

    @Mock
    private CalendarRepository calendarRepository;

    @Mock
    private CalendarMapper calendarMapper;

    @InjectMocks
    private CalendarServiceImpl calendarService;

    private Calendar mockCalendar1;
    private Calendar mockCalendar2;
    private CalendarInfoDto mockInfoDto1;
    private CalendarInfoDto mockInfoDto2;

    @BeforeEach
    void setUp() {
        mockCalendar1 = createCalendar(
            1L,
            "팀 회의",
            false,
            LocalDateTime.of(2025, 1, 15, 14, 0),
            LocalDateTime.of(2025, 1, 15, 15, 0),
            "#FF5733"
        );

        mockCalendar2 = createCalendar(
            2L,
            "프로젝트 마감",
            true,
            LocalDateTime.of(2025, 1, 20, 0, 0),
            LocalDateTime.of(2025, 1, 20, 23, 59),
            "#3498DB"
        );

        mockInfoDto1 = CalendarInfoDto.builder()
            .id(1L)
            .title("팀 회의")
            .allDay(false)
            .startDatetime(LocalDateTime.of(2025, 1, 15, 14, 0))
            .endDatetime(LocalDateTime.of(2025, 1, 15, 15, 0))
            .color("#FF5733")
            .build();

        mockInfoDto2 = CalendarInfoDto.builder()
            .id(2L)
            .title("프로젝트 마감")
            .allDay(true)
            .startDatetime(LocalDateTime.of(2025, 1, 20, 0, 0))
            .endDatetime(LocalDateTime.of(2025, 1, 20, 23, 59))
            .color("#3498DB")
            .build();
    }

    @Test
    @DisplayName("일정 목록 조회 - 성공")
    void getCalendarList_Success() {
        // Given
        DateDto dateDto = new DateDto(
            LocalDateTime.of(2025, 1, 1, 0, 0),
            LocalDateTime.of(2025, 1, 31, 23, 59)
        );

        List<Calendar> mockCalendars = Arrays.asList(mockCalendar1, mockCalendar2);
        List<CalendarInfoDto> mockInfoDtos = Arrays.asList(mockInfoDto1, mockInfoDto2);

        given(calendarRepository.findAllByDateDto(any(DateDto.class)))
            .willReturn(mockCalendars);
        given(calendarMapper.toInfoDtoList(anyList()))
            .willReturn(mockInfoDtos);

        // When
        List<CalendarInfoDto> result = calendarService.getCalendarList(dateDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("팀 회의");
        assertThat(result.get(1).getTitle()).isEqualTo("프로젝트 마감");

        ArgumentCaptor<DateDto> captor = ArgumentCaptor.forClass(DateDto.class);
        verify(calendarRepository, times(1)).findAllByDateDto(captor.capture());
        assertThat(captor.getValue().getStartDateTime()).isEqualTo(dateDto.getStartDateTime());
        assertThat(captor.getValue().getEndDateTime()).isEqualTo(dateDto.getEndDateTime());
        verify(calendarMapper, times(1)).toInfoDtoList(mockCalendars);
    }

    @Test
    @DisplayName("일정 목록 조회 - 빈 결과")
    void getCalendarList_EmptyResult() {
        // Given
        DateDto dateDto = new DateDto(
            LocalDateTime.of(2025, 3, 1, 0, 0),
            LocalDateTime.of(2025, 3, 31, 23, 59)
        );

        given(calendarRepository.findAllByDateDto(any(DateDto.class)))
            .willReturn(Arrays.asList());
        given(calendarMapper.toInfoDtoList(anyList()))
            .willReturn(Arrays.asList());

        // When
        List<CalendarInfoDto> result = calendarService.getCalendarList(dateDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        ArgumentCaptor<DateDto> captor = ArgumentCaptor.forClass(DateDto.class);
        verify(calendarRepository, times(1)).findAllByDateDto(captor.capture());
        assertThat(captor.getValue().getStartDateTime()).isEqualTo(dateDto.getStartDateTime());
        assertThat(captor.getValue().getEndDateTime()).isEqualTo(dateDto.getEndDateTime());
        verify(calendarMapper, times(1)).toInfoDtoList(anyList());
    }

    @Test
    @DisplayName("일정 목록 조회 - 입력 없으면 이번 달 범위로 조회")
    void getCalendarList_DefaultToCurrentMonth() {
        // Given
        given(calendarRepository.findAllByDateDto(any(DateDto.class)))
            .willReturn(Arrays.asList());
        given(calendarMapper.toInfoDtoList(anyList()))
            .willReturn(Arrays.asList());

        // When
        List<CalendarInfoDto> result = calendarService.getCalendarList(new DateDto());

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        ArgumentCaptor<DateDto> captor = ArgumentCaptor.forClass(DateDto.class);
        verify(calendarRepository, times(1)).findAllByDateDto(captor.capture());

        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay = today.with(TemporalAdjusters.lastDayOfMonth());
        assertThat(captor.getValue().getStartDateTime()).isEqualTo(firstDay.atStartOfDay());
        assertThat(captor.getValue().getEndDateTime()).isEqualTo(LocalDateTime.of(lastDay, LocalTime.of(23, 59, 59)));
    }

    @Test
    @DisplayName("일정 저장 - 성공")
    void postCalendar_Success() {
        // Given
        CalendarCreateReqDto reqDto = CalendarCreateReqDto.builder()
            .title("새 일정")
            .color("#E74C3C")
            .allDay(false)
            .startDatetime(LocalDateTime.of(2025, 1, 25, 16, 0))
            .endDatetime(LocalDateTime.of(2025, 1, 25, 17, 0))
            .build();

        Calendar savedCalendar = createCalendar(
            3L,
            "새 일정",
            false,
            LocalDateTime.of(2025, 1, 25, 16, 0),
            LocalDateTime.of(2025, 1, 25, 17, 0),
            "#E74C3C"
        );

        given(calendarRepository.save(any(Calendar.class)))
            .willReturn(savedCalendar);

        // When
        Long result = calendarService.postCalendar(reqDto);

        // Then
        assertThat(result).isEqualTo(3L);
        verify(calendarRepository, times(1)).save(any(Calendar.class));
    }

    private Calendar createCalendar(Long id, String title, boolean allDay,
                                    LocalDateTime startDateTime, LocalDateTime endDateTime,
                                    String color) {
        try {
            Calendar calendar = Calendar.class.getDeclaredConstructor().newInstance();

            java.lang.reflect.Field idField = Calendar.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(calendar, id);

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
