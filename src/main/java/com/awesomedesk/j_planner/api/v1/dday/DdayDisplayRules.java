package com.awesomedesk.j_planner.api.v1.dday;

import com.awesomedesk.j_planner.api.v1.dday.DdayRequest.Display;
import com.awesomedesk.j_planner.api.v1.dday.DdayRequest.Option;
import com.awesomedesk.j_planner.common.error.ApiException;

/**
 * D-Day 달력 표시 옵션의 기본값과 조합 규칙 (D-020)
 * <ul>
 *   <li>기본값: COUNTDOWN → 목표 날짜만 / COUNTUP → 100일 단위 + 매년. N일 단위 N=100, 마지막 N일 N=7</li>
 *   <li>COUNTDOWN은 매년 불가, COUNTUP은 마지막 N일·매일 불가</li>
 * </ul>
 */
final class DdayDisplayRules {

    static final int DEFAULT_INTERVAL_DAYS = 100;
    static final int DEFAULT_LAST_DAYS = 7;

    private DdayDisplayRules() {
    }

    static Display defaults(CountType type) {
        boolean up = type == CountType.COUNTUP;
        return new Display(new Option(up, DEFAULT_INTERVAL_DAYS), new Option(false, DEFAULT_LAST_DAYS), false, up);
    }

    /** 보내지 않은(null) 옵션을 기준별 기본값으로 채운다 */
    static Display fill(CountType type, Display display) {
        Display d = defaults(type);
        if (display == null) {
            return d;
        }
        return new Display(
            fill(display.interval(), d.interval()),
            fill(display.lastDays(), d.lastDays()),
            display.daily() != null ? display.daily() : d.daily(),
            display.yearly() != null ? display.yearly() : d.yearly());
    }

    private static Option fill(Option option, Option base) {
        if (option == null) {
            return base;
        }
        return new Option(option.enabled() != null ? option.enabled() : base.enabled(),
            option.days() != null ? option.days() : base.days());
    }

    /** 기준에 없는 옵션을 켜면 400 */
    static void check(CountType type, Display d) {
        if (type == CountType.COUNTDOWN && d.yearly()) {
            throw ApiException.validation("display.yearly", "매년 표시는 당일=1일(COUNTUP)에서만 고를 수 있습니다.");
        }
        if (type == CountType.COUNTUP && d.lastDays().enabled()) {
            throw ApiException.validation("display.lastDays", "마지막 N일 표시는 당일=0일(COUNTDOWN)에서만 고를 수 있습니다.");
        }
        if (type == CountType.COUNTUP && d.daily()) {
            throw ApiException.validation("display.daily", "매일 표시는 당일=0일(COUNTDOWN)에서만 고를 수 있습니다.");
        }
    }
}
