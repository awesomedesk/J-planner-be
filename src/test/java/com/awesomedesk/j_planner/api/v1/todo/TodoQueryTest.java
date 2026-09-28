package com.awesomedesk.j_planner.api.v1.todo;

import static com.awesomedesk.j_planner.support.ApiExceptionAssertions.assertErrorCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.awesomedesk.j_planner.common.error.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Todo 조회 조건 조합 (08-api-design.md 5절): date / from+to(+scheduled) / completedOn 중 하나만 — DB 없이 */
class TodoQueryTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 25);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 30);

    @Test
    @DisplayName("date만 → 그날 박스")
    void box() {
        assertThat(TodoQuery.of(D1, null, null, null, null)).isEqualTo(new TodoQuery.Box(D1));
    }

    @Test
    @DisplayName("from+to → 기간, scheduled는 없으면 false")
    void range() {
        assertThat(TodoQuery.of(null, D1, D2, null, null)).isEqualTo(new TodoQuery.Range(D1, D2, false));
        assertThat(TodoQuery.of(null, D1, D2, true, null)).isEqualTo(new TodoQuery.Range(D1, D2, true));
    }

    @Test
    @DisplayName("completedOn만 → 그날 완료한 Todo")
    void completedOn() {
        assertThat(TodoQuery.of(null, null, null, null, D1)).isEqualTo(new TodoQuery.CompletedOn(D1));
    }

    @Test
    @DisplayName("없음·둘 이상·from만·to만·scheduled를 date와 함께 → INVALID_QUERY")
    void invalid() {
        assertErrorCode(() -> TodoQuery.of(null, null, null, null, null), ErrorCode.INVALID_QUERY);
        assertErrorCode(() -> TodoQuery.of(D1, D1, D2, null, null), ErrorCode.INVALID_QUERY);
        assertErrorCode(() -> TodoQuery.of(D1, null, null, null, D1), ErrorCode.INVALID_QUERY);
        assertErrorCode(() -> TodoQuery.of(null, D1, null, null, null), ErrorCode.INVALID_QUERY);
        assertErrorCode(() -> TodoQuery.of(null, null, D2, null, null), ErrorCode.INVALID_QUERY);
        assertErrorCode(() -> TodoQuery.of(D1, null, null, true, null), ErrorCode.INVALID_QUERY);
    }
}
