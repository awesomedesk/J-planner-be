package com.awesomedesk.j_planner.api.v1.todo;

import com.awesomedesk.j_planner.common.error.ApiException;
import java.time.LocalTime;

/**
 * Todo 시간은 그날 안에서 끝난다: 시작 + 길이 ≤ 24:00 (종료 00:00 = 그날 자정까지, 자정 넘김은 막음).
 * 화면 규칙(US-12)과 같게 API도 막는다. 길이 1~1440분은 요청 검증(TodoRequest)이 따로 본다.
 */
final class TodoTimeRule {

    private static final int MINUTES_PER_DAY = 24 * 60;

    private TodoTimeRule() {
    }

    static void check(LocalTime start, int durationMinutes) {
        int startMinutes = start.getHour() * 60 + start.getMinute();
        if (startMinutes + durationMinutes > MINUTES_PER_DAY) {
            throw ApiException.validation("time.durationMinutes", "Todo 시간은 그날 자정(00:00)까지만 정할 수 있습니다.");
        }
    }
}
