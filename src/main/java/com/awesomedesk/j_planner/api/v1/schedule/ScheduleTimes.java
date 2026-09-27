package com.awesomedesk.j_planner.api.v1.schedule;

import com.awesomedesk.j_planner.common.error.ApiException;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 일정의 시작·종료 일시를 정리하고 검사한 값 (US-05, 08-api-design.md 4절).
 * <ul>
 *   <li>1초 미만은 버린다</li>
 *   <li>종일이면 시작 = 그날 00:00:00, 종료 = 끝나는 날 23:59:59. 종료 날짜 ≥ 시작 날짜</li>
 *   <li>시간 일정이면 종료 &gt; 시작</li>
 * </ul>
 */
record ScheduleTimes(LocalDateTime start, LocalDateTime end) {

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    static ScheduleTimes of(boolean allDay, LocalDateTime start, LocalDateTime end) {
        LocalDateTime s = start.withNano(0);
        LocalDateTime e = end.withNano(0);
        if (allDay) {
            if (e.toLocalDate().isBefore(s.toLocalDate())) {
                throw ApiException.validation("end", "종료 날짜는 시작 날짜와 같거나 뒤여야 합니다.");
            }
            return new ScheduleTimes(s.toLocalDate().atStartOfDay(), e.toLocalDate().atTime(END_OF_DAY));
        }
        if (!e.isAfter(s)) {
            throw ApiException.validation("end", "종료 일시는 시작 일시보다 뒤여야 합니다.");
        }
        return new ScheduleTimes(s, e);
    }
}
