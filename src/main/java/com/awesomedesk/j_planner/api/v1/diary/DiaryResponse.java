package com.awesomedesk.j_planner.api.v1.diary;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 일기 (08-api-design.md 7절). 날짜가 곧 주소라 id를 주지 않는다 */
public record DiaryResponse(LocalDate date, String content, LocalDateTime updatedAt) {

    static DiaryResponse of(Diary d) {
        return new DiaryResponse(d.getDate(), d.getContent(), d.getUpdatedAt());
    }
}
