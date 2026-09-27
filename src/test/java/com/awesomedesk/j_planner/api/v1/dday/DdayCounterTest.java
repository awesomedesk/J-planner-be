package com.awesomedesk.j_planner.api.v1.dday;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-Day 값과 표시 글자 (08-api-design.md 6절, D-012, D-032) — DB 없이 */
class DdayCounterTest {

    private static final LocalDate TARGET = LocalDate.of(2026, 9, 28);

    private static DdayCounter.Count count(CountType type, String date) {
        return DdayCounter.count(type, TARGET, LocalDate.parse(date));
    }

    @Test
    @DisplayName("당일=0일(COUNTDOWN): 전 D-3, 당일 D-Day(0), 후 D+2")
    void countdown() {
        assertThat(count(CountType.COUNTDOWN, "2026-09-25")).isEqualTo(new DdayCounter.Count(-3, "D-3"));
        assertThat(count(CountType.COUNTDOWN, "2026-09-28")).isEqualTo(new DdayCounter.Count(0, "D-Day"));
        assertThat(count(CountType.COUNTDOWN, "2026-09-30")).isEqualTo(new DdayCounter.Count(2, "D+2"));
    }

    @Test
    @DisplayName("당일=1일(COUNTUP): 전 D-3, 당일 D+1(1일째), 이틀 뒤 D+3")
    void countup() {
        assertThat(count(CountType.COUNTUP, "2026-09-25")).isEqualTo(new DdayCounter.Count(-3, "D-3"));
        assertThat(count(CountType.COUNTUP, "2026-09-28")).isEqualTo(new DdayCounter.Count(1, "D+1"));
        assertThat(count(CountType.COUNTUP, "2026-09-30")).isEqualTo(new DdayCounter.Count(3, "D+3"));
    }

    @Test
    @DisplayName("해를 넘는 큰 값도 날짜 차이 그대로 (D-1000)")
    void large() {
        assertThat(DdayCounter.count(CountType.COUNTDOWN, LocalDate.of(2029, 6, 20), LocalDate.of(2026, 9, 24)).label())
            .isEqualTo("D-1000");
    }
}
