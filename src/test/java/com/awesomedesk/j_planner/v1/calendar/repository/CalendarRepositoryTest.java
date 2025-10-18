package com.awesomedesk.j_planner.v1.calendar.repository;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.v1.calendar.domain.Calendar;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("CalendarRepository 테스트")
class CalendarRepositoryTest {

    @Autowired
    private CalendarRepository calendarRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Calendar calendar1;
    private Calendar calendar2;
    private Calendar calendar3;

    @BeforeEach
    void setUp() {
        // 2025년 1월 15일 회의 일정
        calendar1 = createCalendar(
            "팀 회의",
            false,
            LocalDateTime.of(2025, 1, 15, 14, 0),
            LocalDateTime.of(2025, 1, 15, 15, 0),
            "#FF5733"
        );

        // 2025년 1월 20일 프로젝트 마감 (종일)
        calendar2 = createCalendar(
            "프로젝트 마감",
            true,
            LocalDateTime.of(2025, 1, 20, 0, 0),
            LocalDateTime.of(2025, 1, 20, 23, 59),
            "#3498DB"
        );

        // 2025년 2월 5일 외부 일정 (범위 밖)
        calendar3 = createCalendar(
            "외부 미팅",
            false,
            LocalDateTime.of(2025, 2, 5, 10, 0),
            LocalDateTime.of(2025, 2, 5, 11, 0),
            "#2ECC71"
        );

        entityManager.persist(calendar1);
        entityManager.persist(calendar2);
        entityManager.persist(calendar3);
        entityManager.flush();
    }

    @Test
    @DisplayName("날짜 범위로 일정 목록 조회 - 성공")
    void findAllByDateDto_Success() {
        // Given
        DateDto dateDto = new DateDto(
            LocalDateTime.of(2025, 1, 1, 0, 0),
            LocalDateTime.of(2025, 1, 31, 23, 59)
        );

        // When
        List<Calendar> result = calendarRepository.findAllByDateDto(dateDto);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("팀 회의");
        assertThat(result.get(1).getTitle()).isEqualTo("프로젝트 마감");
    }

    @Test
    @DisplayName("날짜 범위로 일정 목록 조회 - 시작일 기준 정렬 확인")
    void findAllByDateDto_OrderByStartDateTime() {
        // Given
        DateDto dateDto = new DateDto(
            LocalDateTime.of(2025, 1, 1, 0, 0),
            LocalDateTime.of(2025, 1, 31, 23, 59)
        );

        // When
        List<Calendar> result = calendarRepository.findAllByDateDto(dateDto);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDateDto().getStartDateTime())
            .isBefore(result.get(1).getDateDto().getStartDateTime());
    }

    @Test
    @DisplayName("날짜 범위로 일정 목록 조회 - 빈 결과")
    void findAllByDateDto_EmptyResult() {
        // Given - 일정이 없는 기간
        DateDto dateDto = new DateDto(
            LocalDateTime.of(2025, 3, 1, 0, 0),
            LocalDateTime.of(2025, 3, 31, 23, 59)
        );

        // When
        List<Calendar> result = calendarRepository.findAllByDateDto(dateDto);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("일정 저장 - 성공")
    void save_Success() {
        // Given
        Calendar newCalendar = createCalendar(
            "새로운 일정",
            false,
            LocalDateTime.of(2025, 1, 25, 16, 0),
            LocalDateTime.of(2025, 1, 25, 17, 0),
            "#E74C3C"
        );

        // When
        Calendar saved = calendarRepository.save(newCalendar);
        entityManager.flush();
        entityManager.clear();

        // Then
        Calendar found = calendarRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("새로운 일정");
        assertThat(found.getColor()).isEqualTo("#E74C3C");
        assertThat(found.isAllDay()).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("삭제된 일정은 조회되지 않음")
    void findAllByDateDto_ExcludeDeleted() {
        // Given
        calendar1.setDeleted(true);
        calendar1.setDeletedAt(LocalDateTime.now());
        entityManager.persist(calendar1);
        entityManager.flush();

        DateDto dateDto = new DateDto(
            LocalDateTime.of(2025, 1, 1, 0, 0),
            LocalDateTime.of(2025, 1, 31, 23, 59)
        );

        // When
        List<Calendar> result = calendarRepository.findAllByDateDto(dateDto);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("프로젝트 마감");
    }

    @Test
    @DisplayName("겹치는 일정 조회 테스트")
    void findAllByDateDto_OverlappingEvents() {
        // Given - 기존 일정과 겹치는 기간
        DateDto dateDto = new DateDto(
            LocalDateTime.of(2025, 1, 15, 14, 30), // 회의 중간 시간
            LocalDateTime.of(2025, 1, 15, 14, 45)
        );

        // When
        List<Calendar> result = calendarRepository.findAllByDateDto(dateDto);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("팀 회의");
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
