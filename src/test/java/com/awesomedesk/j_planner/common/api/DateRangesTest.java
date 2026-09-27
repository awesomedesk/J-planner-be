package com.awesomedesk.j_planner.common.api;

import static com.awesomedesk.j_planner.support.ApiExceptionAssertions.assertErrorCode;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.awesomedesk.j_planner.common.error.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 기간 조회 조건 (D-032: 최대 62일, from·to 포함) — DB 없이 */
class DateRangesTest {

    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);

    @Test
    @DisplayName("하루(from = to)와 정확히 62일은 된다")
    void allowed() {
        assertThatCode(() -> DateRanges.check(SEP_1, SEP_1)).doesNotThrowAnyException();
        assertThatCode(() -> DateRanges.check(SEP_1, LocalDate.of(2026, 11, 1))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("63일 이상 → INVALID_QUERY")
    void tooLong() {
        assertErrorCode(() -> DateRanges.check(SEP_1, LocalDate.of(2026, 11, 2)), ErrorCode.INVALID_QUERY);
    }

    @Test
    @DisplayName("to가 from보다 앞 → INVALID_QUERY")
    void reversed() {
        assertErrorCode(() -> DateRanges.check(SEP_1, SEP_1.minusDays(1)), ErrorCode.INVALID_QUERY);
    }
}
