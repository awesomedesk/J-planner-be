package com.awesomedesk.j_planner.api.v1.memo;

import com.awesomedesk.j_planner.common.api.MaxChars;

/** 메모 추가 / PATCH 합친 결과 (08-api-design.md 8절). 둘 중 하나는 비어도 되지만 둘 다 비면 400 (MemoRules) */
public record MemoRequest(
    @MaxChars(value = 255, message = "제목은 255자까지입니다.")
    String title,

    String content
) {
}
