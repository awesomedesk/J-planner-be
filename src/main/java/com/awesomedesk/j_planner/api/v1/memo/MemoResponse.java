package com.awesomedesk.j_planner.api.v1.memo;

import java.time.LocalDateTime;

/** 메모 (08-api-design.md 8절) */
public record MemoResponse(Long id, String title, String content, LocalDateTime createdAt, LocalDateTime updatedAt) {

    static MemoResponse of(Memo m) {
        return new MemoResponse(m.getId(), m.getTitle(), m.getContent(), m.getCreatedAt(), m.getUpdatedAt());
    }

    MemoRequest toRequest() {
        return new MemoRequest(title, content);
    }
}
