package com.awesomedesk.j_planner.api.v1.diary;

import jakarta.validation.constraints.NotBlank;

/** 일기 쓰기·덮어쓰기 (08-api-design.md 7절). 내용을 모두 지우는 것은 400, 지우려면 DELETE */
public record DiaryRequest(
    @NotBlank(message = "내용을 입력하세요. 일기를 지우려면 삭제를 누르세요.")
    String content
) {
}
