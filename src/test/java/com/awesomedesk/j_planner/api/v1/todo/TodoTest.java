package com.awesomedesk.j_planner.api.v1.todo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Todo 엔티티 규칙 (완료·지난 미완료) — DB 없이 */
class TodoTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 9, 25, 9, 0);

    private static Todo todo(TodoType type, LocalDate start, LocalDate end) {
        return new Todo(new Todo.Values(1L, "할 일", type, start, end, null, null, null), 0);
    }

    @Test
    @DisplayName("새 Todo는 미완료")
    void newIsIncomplete() {
        Todo t = todo(TodoType.DAY, TODAY, TODAY);
        assertThat(t.isCompleted()).isFalse();
        assertThat(t.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("완료하면 completedAt = 지금, 다시 완료해도 처음 시각 유지, 취소하면 null")
    void markCompleted() {
        Todo t = todo(TodoType.DAY, TODAY, TODAY);
        t.markCompleted(true, NINE);
        assertThat(t.isCompleted()).isTrue();
        assertThat(t.getCompletedAt()).isEqualTo(NINE);

        t.markCompleted(true, NINE.plusHours(3));
        assertThat(t.getCompletedAt()).isEqualTo(NINE);

        t.markCompleted(false, NINE.plusHours(4));
        assertThat(t.isCompleted()).isFalse();
        assertThat(t.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("지난 미완료: 마감일이 오늘보다 앞이고 미완료일 때만 (TODO-13)")
    void overdue() {
        assertThat(todo(TodoType.DAY, TODAY.minusDays(1), TODAY.minusDays(1)).isOverdue(TODAY)).isTrue();
        assertThat(todo(TodoType.DAY, TODAY, TODAY).isOverdue(TODAY)).isFalse();
        // 기간 Todo는 마감일 기준: 시작이 지났어도 마감이 오늘 이후면 지남 아님
        assertThat(todo(TodoType.PERIOD, TODAY.minusDays(3), TODAY).isOverdue(TODAY)).isFalse();

        Todo done = todo(TodoType.DAY, TODAY.minusDays(1), TODAY.minusDays(1));
        done.markCompleted(true, NINE);
        assertThat(done.isOverdue(TODAY)).isFalse();
    }
}
