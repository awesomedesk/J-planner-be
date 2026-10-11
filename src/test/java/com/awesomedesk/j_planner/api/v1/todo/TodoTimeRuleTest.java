package com.awesomedesk.j_planner.api.v1.todo;

import static com.awesomedesk.j_planner.support.ApiExceptionAssertions.assertFieldError;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Todo 시간은 그날 안에서 끝난다 — 종료 00:00(그날 자정)까지, 자정 넘김은 막음 (US-12, 09-backlog US-12 메모) — DB 없이 */
class TodoTimeRuleTest {

    private static void ok(String start, int minutes) {
        assertThatCode(() -> TodoTimeRule.check(LocalTime.parse(start), minutes)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("US-12: 그날 자정(24:00)에 끝나는 것까지 된다")
    void endsByMidnight() {
        ok("23:00", 60);
        ok("00:00", 1440);
        ok("10:00", 30);
    }

    @Test
    @DisplayName("US-12: 자정을 넘기면 400 (time.durationMinutes)")
    void crossingMidnight() {
        assertFieldError(() -> TodoTimeRule.check(LocalTime.parse("23:00"), 120), "time.durationMinutes");
        assertFieldError(() -> TodoTimeRule.check(LocalTime.parse("23:59"), 2), "time.durationMinutes");
        assertFieldError(() -> TodoTimeRule.check(LocalTime.parse("00:01"), 1440), "time.durationMinutes");
    }
}
