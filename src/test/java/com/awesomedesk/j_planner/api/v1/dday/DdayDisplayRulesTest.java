package com.awesomedesk.j_planner.api.v1.dday;

import static com.awesomedesk.j_planner.support.ApiExceptionAssertions.assertFieldError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.awesomedesk.j_planner.api.v1.dday.DdayRequest.Display;
import com.awesomedesk.j_planner.api.v1.dday.DdayRequest.Option;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-Day 달력 표시 옵션의 기본값과 조합 규칙 (D-020) — DB 없이 */
class DdayDisplayRulesTest {

    private static final Option OFF_100 = new Option(false, 100);
    private static final Option OFF_7 = new Option(false, 7);

    @Test
    @DisplayName("기본값: 당일=0일 → 목표 날짜만 (옵션 모두 꺼짐, N=100·마지막 7일)")
    void countdownDefaults() {
        assertThat(DdayDisplayRules.defaults(CountType.COUNTDOWN)).isEqualTo(new Display(OFF_100, OFF_7, false, false));
    }

    @Test
    @DisplayName("기본값: 당일=1일 → 100일 단위 + 매년")
    void countupDefaults() {
        assertThat(DdayDisplayRules.defaults(CountType.COUNTUP)).isEqualTo(new Display(new Option(true, 100), OFF_7, false, true));
    }

    @Test
    @DisplayName("보내지 않은 옵션은 기본값으로 채운다")
    void fillDefaults() {
        Display partial = new Display(null, new Option(true, null), true, null);
        assertThat(DdayDisplayRules.fill(CountType.COUNTDOWN, partial))
            .isEqualTo(new Display(OFF_100, new Option(true, 7), true, false));
        assertThat(DdayDisplayRules.fill(CountType.COUNTUP, null)).isEqualTo(DdayDisplayRules.defaults(CountType.COUNTUP));
    }

    @Test
    @DisplayName("당일=0일: N일 단위·마지막 N일·매일은 되고 매년은 안 된다")
    void countdownCombos() {
        assertThatCode(() -> DdayDisplayRules.check(CountType.COUNTDOWN,
            new Display(new Option(true, 100), new Option(true, 7), true, false))).doesNotThrowAnyException();
        assertFieldError(() -> DdayDisplayRules.check(CountType.COUNTDOWN, new Display(OFF_100, OFF_7, false, true)),
            "display.yearly");
    }

    @Test
    @DisplayName("당일=1일: N일 단위·매년은 되고 마지막 N일·매일은 안 된다")
    void countupCombos() {
        assertThatCode(() -> DdayDisplayRules.check(CountType.COUNTUP, DdayDisplayRules.defaults(CountType.COUNTUP)))
            .doesNotThrowAnyException();
        assertFieldError(() -> DdayDisplayRules.check(CountType.COUNTUP, new Display(OFF_100, new Option(true, 7), false, false)),
            "display.lastDays");
        assertFieldError(() -> DdayDisplayRules.check(CountType.COUNTUP, new Display(OFF_100, OFF_7, true, false)),
            "display.daily");
    }
}
