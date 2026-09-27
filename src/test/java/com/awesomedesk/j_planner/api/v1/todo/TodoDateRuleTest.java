package com.awesomedesk.j_planner.api.v1.todo;

import static com.awesomedesk.j_planner.support.ApiExceptionAssertions.assertFieldError;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 종류별 날짜 규칙 (US-12, 08-api-design.md 5절) — DB 없이. 주 시작 요일은 인자로 받는다 (D-041) */
class TodoDateRuleTest {

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    private static void ok(TodoType type, String start, String end) {
        assertThatCode(() -> TodoDateRule.check(type, d(start), d(end), SUNDAY)).doesNotThrowAnyException();
    }

    private static void bad(TodoType type, String start, String end, String field) {
        assertFieldError(() -> TodoDateRule.check(type, d(start), d(end), SUNDAY), field);
    }

    @Nested
    @DisplayName("하루(DAY)")
    class Day {
        @Test
        @DisplayName("시작일 = 마감일")
        void rule() {
            ok(TodoType.DAY, "2026-09-25", "2026-09-25");
            bad(TodoType.DAY, "2026-09-25", "2026-09-26", "endDate");
        }
    }

    @Nested
    @DisplayName("기간(PERIOD)")
    class Period {
        @Test
        @DisplayName("마감일 ≥ 시작일 (하루짜리 기간도 된다)")
        void rule() {
            ok(TodoType.PERIOD, "2026-09-24", "2026-09-30");
            ok(TodoType.PERIOD, "2026-09-24", "2026-09-24");
            bad(TodoType.PERIOD, "2026-09-25", "2026-09-24", "endDate");
        }
    }

    @Nested
    @DisplayName("주간(WEEK)")
    class Week {
        @Test
        @DisplayName("주 시작이 일요일이면 일~토")
        void sunday() {
            ok(TodoType.WEEK, "2026-09-20", "2026-09-26");
            bad(TodoType.WEEK, "2026-09-21", "2026-09-27", "startDate");
            bad(TodoType.WEEK, "2026-09-20", "2026-09-25", "endDate");
        }

        @Test
        @DisplayName("주 시작이 월요일이면 월~일")
        void monday() {
            assertThatCode(() -> TodoDateRule.check(TodoType.WEEK, d("2026-09-21"), d("2026-09-27"), MONDAY))
                .doesNotThrowAnyException();
            assertFieldError(() -> TodoDateRule.check(TodoType.WEEK, d("2026-09-20"), d("2026-09-26"), MONDAY), "startDate");
        }

        @Test
        @DisplayName("해·달을 넘는 주도 된다")
        void acrossYear() {
            ok(TodoType.WEEK, "2026-12-27", "2027-01-02");
        }
    }

    @Nested
    @DisplayName("월간(MONTH)")
    class Month {
        @Test
        @DisplayName("1일 ~ 그달 말일 (30일·31일·2월·윤년)")
        void rule() {
            ok(TodoType.MONTH, "2026-09-01", "2026-09-30");
            ok(TodoType.MONTH, "2026-10-01", "2026-10-31");
            ok(TodoType.MONTH, "2027-02-01", "2027-02-28");
            ok(TodoType.MONTH, "2028-02-01", "2028-02-29");
            bad(TodoType.MONTH, "2026-09-02", "2026-09-30", "startDate");
            bad(TodoType.MONTH, "2026-09-01", "2026-10-01", "endDate");
            bad(TodoType.MONTH, "2027-02-01", "2027-02-27", "endDate");
        }
    }
}
