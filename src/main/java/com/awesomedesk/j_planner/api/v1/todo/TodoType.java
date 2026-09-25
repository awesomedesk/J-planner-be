package com.awesomedesk.j_planner.api.v1.todo;

/** Todo 종류 (D-007, 08-api-design.md 5절) */
public enum TodoType {
    /** 하루: 시작일 = 마감일 */
    DAY,
    /** 기간: 시작일 ~ 마감일 */
    PERIOD,
    /** 주간 목표: 설정의 주 시작 요일부터 7일 */
    WEEK,
    /** 월간 목표: 1일 ~ 말일 */
    MONTH
}
