package com.awesomedesk.j_planner.api.v1.dday;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * D-Day 값과 표시 글자 (08-api-design.md 6절, D-012, D-032)
 * <ul>
 *   <li>COUNTDOWN: value = 날짜 - 목표일. 전 {@code D-3}, 당일 {@code D-Day}, 후 {@code D+2}</li>
 *   <li>COUNTUP: 목표일부터 1, 2, 3 … (당일 {@code D+1}), 전이면 날짜 - 목표일 (음수, {@code D-3})</li>
 * </ul>
 */
public final class DdayCounter {

    public record Count(int value, String label) {
    }

    private DdayCounter() {
    }

    public static Count count(CountType type, LocalDate target, LocalDate date) {
        int diff = (int) ChronoUnit.DAYS.between(target, date);
        if (diff < 0) {
            return new Count(diff, "D" + diff);
        }
        if (type == CountType.COUNTUP) {
            return new Count(diff + 1, "D+" + (diff + 1));
        }
        return diff == 0 ? new Count(0, "D-Day") : new Count(diff, "D+" + diff);
    }
}
