package com.awesomedesk.j_planner.api.v1.todo;

import com.awesomedesk.j_planner.common.error.ApiException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * 종류별 날짜 규칙 (US-12, 08-api-design.md 5절). 날짜는 FE가 계산해 보내고 서버는 검사만 한다.
 * <ul>
 *   <li>하루: 시작일 = 마감일</li>
 *   <li>기간: 마감일 ≥ 시작일</li>
 *   <li>주간: 시작일 = 주 시작 요일, 마감일 = 시작일 + 6일 (주 시작 요일은 저장 시점의 설정, D-041)</li>
 *   <li>월간: 1일 ~ 그달 말일</li>
 * </ul>
 */
final class TodoDateRule {

    private TodoDateRule() {
    }

    static void check(TodoType type, LocalDate start, LocalDate end, DayOfWeek weekStart) {
        switch (type) {
            case DAY -> {
                if (!end.equals(start)) {
                    throw ApiException.validation("endDate", "하루 Todo는 시작일과 마감일이 같아야 합니다.");
                }
            }
            case PERIOD -> {
                if (end.isBefore(start)) {
                    throw ApiException.validation("endDate", "마감일은 시작일과 같거나 뒤여야 합니다.");
                }
            }
            case WEEK -> {
                if (start.getDayOfWeek() != weekStart) {
                    throw ApiException.validation("startDate",
                        "주간 목표의 시작일은 주 시작 요일(" + (weekStart == DayOfWeek.SUNDAY ? "일요일" : "월요일") + ")이어야 합니다.");
                }
                if (!end.equals(start.plusDays(6))) {
                    throw ApiException.validation("endDate", "주간 목표의 마감일은 시작일 + 6일입니다.");
                }
            }
            case MONTH -> {
                if (start.getDayOfMonth() != 1) {
                    throw ApiException.validation("startDate", "월간 목표의 시작일은 그달 1일이어야 합니다.");
                }
                if (!end.equals(start.with(TemporalAdjusters.lastDayOfMonth()))) {
                    throw ApiException.validation("endDate", "월간 목표의 마감일은 그달 말일이어야 합니다.");
                }
            }
        }
    }
}
