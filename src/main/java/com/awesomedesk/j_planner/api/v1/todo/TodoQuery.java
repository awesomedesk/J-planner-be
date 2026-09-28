package com.awesomedesk.j_planner.api.v1.todo;

import com.awesomedesk.j_planner.common.error.ApiException;
import java.time.LocalDate;

/**
 * Todo 목록 조회 조건 (08-api-design.md 5절). date / from+to(+scheduled) / completedOn 중 하나만.
 */
public sealed interface TodoQuery {

    /** 그날 박스 (오늘이면 지난 미완료 포함) */
    record Box(LocalDate date) implements TodoQuery {
    }

    /** 기간과 겹치는 Todo. scheduled면 시간 있는 것만 (시간표) */
    record Range(LocalDate from, LocalDate to, boolean scheduled) implements TodoQuery {
    }

    /** 그날 완료한 Todo (DIARY-04) */
    record CompletedOn(LocalDate date) implements TodoQuery {
    }

    static TodoQuery of(LocalDate date, LocalDate from, LocalDate to, Boolean scheduled, LocalDate completedOn) {
        boolean byRange = from != null || to != null;
        int conditions = (date != null ? 1 : 0) + (byRange ? 1 : 0) + (completedOn != null ? 1 : 0);
        if (conditions != 1) {
            throw ApiException.invalidQuery("조회 조건은 date, from+to, completedOn 중 하나만 보내세요.");
        }
        if (scheduled != null && !byRange) {
            throw ApiException.invalidQuery("scheduled는 from+to와 함께만 쓸 수 있습니다.");
        }
        if (date != null) {
            return new Box(date);
        }
        if (completedOn != null) {
            return new CompletedOn(completedOn);
        }
        if (from == null || to == null) {
            throw ApiException.invalidQuery("from과 to를 함께 보내세요.");
        }
        return new Range(from, to, Boolean.TRUE.equals(scheduled));
    }
}
