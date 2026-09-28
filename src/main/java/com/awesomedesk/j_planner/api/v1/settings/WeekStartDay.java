package com.awesomedesk.j_planner.api.v1.settings;

import java.time.DayOfWeek;

/** 주 시작 요일 (CAL-04) */
public enum WeekStartDay {
    SUN(DayOfWeek.SUNDAY),
    MON(DayOfWeek.MONDAY);

    private final DayOfWeek dayOfWeek;

    WeekStartDay(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public DayOfWeek toDayOfWeek() {
        return dayOfWeek;
    }
}
