package com.awesomedesk.j_planner.api.v1.schedule;

import static com.awesomedesk.j_planner.support.ApiExceptionAssertions.assertFieldError;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 일정 시작·종료 일시 정리와 검사 (US-05, 08-api-design.md 4절) — DB 없이 */
class ScheduleTimesTest {

    private static LocalDateTime t(String s) {
        return LocalDateTime.parse(s);
    }

    @Test
    @DisplayName("시간 일정: 그대로 두되 1초 미만은 버린다")
    void timed() {
        ScheduleTimes times = ScheduleTimes.of(false, t("2026-09-25T10:00:00.123"), t("2026-09-25T11:30:00"));
        assertThat(times.start()).isEqualTo(t("2026-09-25T10:00:00"));
        assertThat(times.end()).isEqualTo(t("2026-09-25T11:30:00"));
    }

    @Test
    @DisplayName("시간 일정: 종료는 시작보다 뒤여야 한다 (같으면 안 됨)")
    void timedOrder() {
        assertFieldError(() -> ScheduleTimes.of(false, t("2026-09-25T10:00:00"), t("2026-09-25T10:00:00")), "end");
        assertFieldError(() -> ScheduleTimes.of(false, t("2026-09-25T10:00:00"), t("2026-09-25T09:00:00")), "end");
    }

    @Test
    @DisplayName("종일: 시작 날 00:00:00 ~ 끝나는 날 23:59:59로 맞춘다")
    void allDay() {
        ScheduleTimes times = ScheduleTimes.of(true, t("2026-09-28T13:00:00"), t("2026-09-30T09:00:00"));
        assertThat(times.start()).isEqualTo(t("2026-09-28T00:00:00"));
        assertThat(times.end()).isEqualTo(t("2026-09-30T23:59:59"));
    }

    @Test
    @DisplayName("종일: 하루짜리는 시각이 거꾸로여도 된다. 종료 날짜가 시작 날짜보다 앞이면 안 된다")
    void allDayOrder() {
        ScheduleTimes oneDay = ScheduleTimes.of(true, t("2026-09-25T18:00:00"), t("2026-09-25T08:00:00"));
        assertThat(oneDay.end()).isEqualTo(t("2026-09-25T23:59:59"));
        assertFieldError(() -> ScheduleTimes.of(true, t("2026-09-25T00:00:00"), t("2026-09-24T23:00:00")), "end");
    }
}
