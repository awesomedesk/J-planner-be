package com.awesomedesk.j_planner.api.v1.todo;

import com.awesomedesk.j_planner.api.v1.category.CategoryRepository;
import com.awesomedesk.j_planner.api.v1.settings.SettingsService;
import com.awesomedesk.j_planner.common.api.DateRanges;
import com.awesomedesk.j_planner.common.api.JsonMergePatch;
import com.awesomedesk.j_planner.common.api.Positions;
import com.awesomedesk.j_planner.common.api.RequestValidator;
import com.awesomedesk.j_planner.common.error.ApiException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Todo (US-12~16, 08-api-design.md 5절). 모두 로그인한 회원(userId)의 Todo만 다룬다 (US-32)
 * - 종류별 날짜 규칙은 FE가 계산해 보내고 서버는 검사만 한다
 * - 새 Todo는 맨 뒤 (D-029), 기간·주간·월간도 한 번 체크 = 전체 완료 (D-027)
 * - 오늘 박스에는 지난 미완료도 함께 (D-029), 순서는 제자리 (D-015)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final CategoryRepository categoryRepository;
    private final JsonMergePatch jsonMergePatch;
    private final RequestValidator requestValidator;
    private final SettingsService settingsService;
    private final Clock clock;

    // ------------------------------------------------------------------ 조회

    /** 그날 박스. 오늘이면 지난 미완료도 함께 */
    public List<TodoResponse> box(Long userId, LocalDate date, List<Long> categoryIds) {
        LocalDate today = today();
        CategoryFilter f = CategoryFilter.of(categoryIds);
        return toResponses(todoRepository.findBox(userId, date, date.equals(today), f.all(), f.ids()), today);
    }

    /** 기간과 겹치는 Todo. scheduled면 시간 있는 것만 (시간표 블록) */
    public List<TodoResponse> range(Long userId, LocalDate from, LocalDate to, boolean scheduled, List<Long> categoryIds) {
        DateRanges.check(from, to);
        CategoryFilter f = CategoryFilter.of(categoryIds);
        List<Todo> todos = scheduled
            ? todoRepository.findScheduled(userId, from, to, f.all(), f.ids())
            : todoRepository.findOverlapping(userId, from, to, f.all(), f.ids());
        return toResponses(todos, today());
    }

    /** 그날 완료한 Todo (DIARY-04) */
    public List<TodoResponse> completedOn(Long userId, LocalDate date, List<Long> categoryIds) {
        CategoryFilter f = CategoryFilter.of(categoryIds);
        return toResponses(todoRepository.findCompletedBetween(userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(),
            f.all(), f.ids()), today());
    }

    public TodoResponse get(Long userId, Long id) {
        return TodoResponse.of(find(userId, id), today());
    }

    // ------------------------------------------------------------------ 변경

    @Transactional
    public TodoResponse create(Long userId, TodoRequest request) {
        int sortOrder = todoRepository.findMaxSortOrder(userId) + 1;
        Todo saved = todoRepository.save(new Todo(userId, toValues(userId, request, true), sortOrder));
        return TodoResponse.of(saved, today());
    }

    @Transactional
    public TodoResponse update(Long userId, Long id, JsonNode patch) {
        Todo todo = find(userId, id);
        LocalDate today = today();
        TodoRequest merged = requestValidator.validate(
            jsonMergePatch.apply(TodoResponse.of(todo, today).toRequest(), patch, TodoRequest.class));
        // 종류·날짜를 바꿀 때만 날짜 규칙을 다시 검사한다 (D-042: 설정을 바꾼 뒤에도 예전 주간 Todo는 고칠 수 있게)
        boolean datesChanged = todo.getType() != merged.type()
            || !todo.getStartDate().equals(merged.startDate()) || !todo.getEndDate().equals(merged.endDate());
        todo.apply(toValues(userId, merged, datesChanged));
        if (merged.completed() == null) {
            throw ApiException.validation("completed", "완료 여부는 true 또는 false입니다.");
        }
        todo.markCompleted(merged.completed(), LocalDateTime.now(clock).withNano(0));
        todoRepository.flush();
        return TodoResponse.of(todo, today);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        todoRepository.delete(find(userId, id));
    }

    /** 순서 이동 (D-030). 내 전체 Todo 순서에서 afterId 바로 뒤로 옮기고 0부터 다시 매긴다 (남의 afterId는 400) */
    @Transactional
    public TodoResponse move(Long userId, Long id, Long afterId) {
        Todo todo = find(userId, id);
        Positions.reorder(todoRepository.findAllOrdered(userId), todo, afterId, 0);
        todoRepository.flush();
        return TodoResponse.of(todo, today());
    }

    // ------------------------------------------------------------------ 내부

    /** 남의 Todo도 '없음'(404) */
    private Todo find(Long userId, Long id) {
        return todoRepository.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Todo를 찾을 수 없습니다: " + id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static List<TodoResponse> toResponses(List<Todo> todos, LocalDate today) {
        return todos.stream()
            .map(t -> TodoResponse.of(t, today))
            .toList();
    }

    /** 카테고리 필터 (없거나 비면 전체). IN ()을 피하려고 전체일 때도 자리 채움 값을 둔다 */
    private record CategoryFilter(boolean all, List<Long> ids) {
        static CategoryFilter of(List<Long> categoryIds) {
            boolean all = categoryIds == null || categoryIds.isEmpty();
            return new CategoryFilter(all, all ? List.of(-1L) : categoryIds);
        }
    }

    /**
     * 검증(종류별 날짜 규칙·카테고리)과 정리를 마친 값
     * @param checkDates 종류별 날짜 규칙을 검사할지 (새로 만들 때, 또는 종류·날짜를 바꿀 때)
     */
    private Todo.Values toValues(Long userId, TodoRequest r, boolean checkDates) {
        LocalDate start = r.startDate();
        LocalDate end = r.endDate();
        if (checkDates) {
            // 주 시작 요일은 주간일 때만 설정에서 읽는다 (저장 시점 기준, D-041)
            DayOfWeek weekStart = r.type() == TodoType.WEEK ? settingsService.weekStartDay(userId) : null;
            TodoDateRule.check(r.type(), start, end, weekStart);
        }

        LocalTime startTime = null;
        Integer duration = null;
        if (r.time() != null) {
            startTime = r.time().start().withSecond(0).withNano(0);
            duration = r.time().durationMinutes();
        }

        Long categoryId = r.categoryId();
        if (categoryId == null) {
            categoryId = categoryRepository.getDefault(userId).getId();
        } else if (!categoryRepository.existsByIdAndUserId(categoryId, userId)) { // 남의 카테고리도 '없는 카테고리'
            throw ApiException.validation("categoryId", "없는 카테고리입니다.");
        }

        return new Todo.Values(categoryId, r.title().strip(), r.type(), start, end, startTime, duration, r.color());
    }
}
