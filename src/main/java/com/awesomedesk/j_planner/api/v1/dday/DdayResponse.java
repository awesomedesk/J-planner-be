package com.awesomedesk.j_planner.api.v1.dday;

import java.time.LocalDate;

/** D-Day (08-api-design.md 6절). today = 오늘 기준 값과 표시 글자 */
public record DdayResponse(
    Long id,
    String title,
    LocalDate targetDate,
    CountType countType,
    DdayRequest.Display display,
    int sortOrder,
    DdayCounter.Count today
) {

    static DdayResponse of(Dday d, LocalDate today) {
        return new DdayResponse(d.getId(), d.getTitle(), d.getTargetDate(), d.getCountType(), d.display(), d.getSortOrder(),
            DdayCounter.count(d.getCountType(), d.getTargetDate(), today));
    }

    /** PATCH 합치기용: 현재 값을 요청 모양으로 */
    DdayRequest toRequest() {
        return new DdayRequest(title, targetDate, countType, display);
    }
}
