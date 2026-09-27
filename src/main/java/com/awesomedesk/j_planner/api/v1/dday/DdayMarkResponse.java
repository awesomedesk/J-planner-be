package com.awesomedesk.j_planner.api.v1.dday;

import java.time.LocalDate;

/** 달력에 표시할 D-Day 한 칸 (08-api-design.md 6절 /dday-marks). 저장된 데이터가 아니라 계산 결과 */
public record DdayMarkResponse(Long ddayId, LocalDate date, String label, DdayMarkCalculator.Kind kind) {
}
