package com.awesomedesk.j_planner.common.api;

import com.awesomedesk.j_planner.common.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 순서 이동 계산. 옮길 항목을 {@code afterId} 바로 뒤(null이면 맨 앞)에 넣은 새 순서를 돌려준다.
 * 호출한 쪽은 돌려받은 순서대로 sort_order를 다시 매긴다 (07-db-design.md 4-4 ④).
 */
public final class Positions {

    private Positions() {
    }

    /**
     * @param ordered 현재 순서대로 정렬된 목록 (옮길 항목 포함)
     * @param moving  옮길 항목
     * @param afterId 이 id를 가진 항목 바로 뒤로. null이면 맨 앞
     * @param idOf    항목의 id
     */
    public static <T> List<T> move(List<T> ordered, T moving, Long afterId, Function<T, Long> idOf) {
        Long movingId = idOf.apply(moving);
        if (Objects.equals(afterId, movingId)) {
            throw ApiException.validation("afterId", "자기 자신 뒤로는 옮길 수 없습니다.");
        }
        List<T> result = new ArrayList<>(ordered);
        result.removeIf(item -> Objects.equals(idOf.apply(item), movingId));
        int insertAt = 0;
        if (afterId != null) {
            int index = -1;
            for (int i = 0; i < result.size(); i++) {
                if (Objects.equals(idOf.apply(result.get(i)), afterId)) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                throw ApiException.validation("afterId", "없는 항목입니다.");
            }
            insertAt = index + 1;
        }
        result.add(insertAt, moving);
        return result;
    }
}
