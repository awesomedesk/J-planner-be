package com.awesomedesk.j_planner.common.api;

import static com.awesomedesk.j_planner.support.ApiExceptionAssertions.assertFieldError;
import static org.assertj.core.api.Assertions.assertThat;

import com.awesomedesk.j_planner.common.domain.Sortable;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 순서 이동 계산 (D-030, 07-db-design.md 4-4 ④) — DB 없이 */
class PositionsTest {

    /** 순서를 가진 항목 (카테고리·Todo·D-Day와 같은 모양) */
    static class Item implements Sortable {
        private final Long id;
        private int sortOrder = -1;

        Item(long id) {
            this.id = id;
        }

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void changeSortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
        }
    }

    @Test
    @DisplayName("reorder: 옮긴 뒤 순서대로 번호를 다시 매긴다 (시작 번호 지정, 카테고리는 미지정 다음 1부터)")
    void reorderRenumbers() {
        Item a = new Item(1), b = new Item(2), c = new Item(3);

        List<Item> result = Positions.reorder(List.of(a, b, c), c, null, 1);

        assertThat(result).containsExactly(c, a, b);
        assertThat(List.of(c.sortOrder, a.sortOrder, b.sortOrder)).containsExactly(1, 2, 3);
    }

    private static final Function<Long, Long> ID = Function.identity();
    private static final List<Long> ORDERED = List.of(1L, 2L, 3L, 4L);

    @Test
    @DisplayName("afterId 바로 뒤로 옮긴다 (앞→뒤, 뒤→앞)")
    void moveAfter() {
        assertThat(Positions.move(ORDERED, 1L, 3L, ID)).containsExactly(2L, 3L, 1L, 4L);
        assertThat(Positions.move(ORDERED, 4L, 1L, ID)).containsExactly(1L, 4L, 2L, 3L);
        assertThat(Positions.move(ORDERED, 2L, 4L, ID)).containsExactly(1L, 3L, 4L, 2L);
    }

    @Test
    @DisplayName("afterId가 null이면 맨 앞")
    void moveToFront() {
        assertThat(Positions.move(ORDERED, 3L, null, ID)).containsExactly(3L, 1L, 2L, 4L);
    }

    @Test
    @DisplayName("이미 그 자리면 순서가 그대로")
    void sameplace() {
        assertThat(Positions.move(ORDERED, 3L, 2L, ID)).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("원래 목록은 바꾸지 않는다")
    void doesNotMutateInput() {
        Positions.move(ORDERED, 1L, 4L, ID);
        assertThat(ORDERED).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("자기 자신 뒤로·없는 afterId → afterId 필드 오류")
    void invalidAfterId() {
        assertFieldError(() -> Positions.move(ORDERED, 2L, 2L, ID), "afterId");
        assertFieldError(() -> Positions.move(ORDERED, 2L, 99L, ID), "afterId");
    }
}
