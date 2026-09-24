package com.awesomedesk.j_planner.common.api;

import com.awesomedesk.j_planner.common.error.ApiException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 기간 조회 조건 검사 (D-032: 한 번에 최대 62일, from·to 날짜 포함).
 */
public final class DateRanges {

    public static final int MAX_DAYS = 62;

    private DateRanges() {
    }

    public static void check(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.invalidQuery("to는 from과 같거나 뒤여야 합니다.");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw ApiException.invalidQuery("조회 기간은 최대 " + MAX_DAYS + "일입니다. (요청: " + days + "일)");
        }
    }
}
