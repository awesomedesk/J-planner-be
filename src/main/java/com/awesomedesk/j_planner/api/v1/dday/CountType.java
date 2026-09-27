package com.awesomedesk.j_planner.api.v1.dday;

/** D-Day 계산 기준 (D-012) */
public enum CountType {
    /** 당일=0일 (남은 날): D-3 → D-Day → D+2 */
    COUNTDOWN,
    /** 당일=1일 (지난 날): 목표일이 D+1 (1일째) */
    COUNTUP
}
