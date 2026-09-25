package com.awesomedesk.j_planner.api.v1.todo;

import com.awesomedesk.j_planner.api.v1.category.CategoryRepository;
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
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Todo (US-12~16, 08-api-design.md 5절)
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
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    // ------------------------------------------------------------------ 조회

    /** 그날 박스. 오늘이면 지난 미완료도 함께 */
    public List<TodoResponse> box(LocalDate date, List<Long> categoryIds) {
        LocalDate today = today();
        return toResponses(todoRepository.findBox(date, date.equals(today)), categoryIds, today);
    }

    /** 기간과 겹치는 Todo. scheduled면 시간 있는 것만 (시간표 블록) */
    public List<TodoResponse> range(LocalDate from, LocalDate to, boolean scheduled, List<Long> categoryIds) {
        DateRanges.check(from, to);
        List<Todo> todos = scheduled ? todoRepository.findScheduled(from, to) : todoRepository.findOverlapping(from, to);
        return toResponses(todos, categoryIds, today());
    }

    /** 그날 완료한 Todo (DIARY-04) */
    public List<TodoResponse> completedOn(LocalDate date, List<Long> categoryIds) {
        return toResponses(todoRepository.findCompletedBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay()),
            categoryIds, today());
    }

    public TodoResponse get(Long id) {
        return TodoResponse.of(find(id), today());
    }

    // ------------------------------------------------------------------ 변경

    @Transactional
    public TodoResponse create(TodoRequest request) {
        int sortOrder = todoRepository.findMaxSortOrder() + 1;
        Todo saved = todoRepository.save(new Todo(toValues(request), sortOrder));
        return TodoResponse.of(saved, today());
    }

    @Transactional
    public TodoResponse update(Long id, JsonNode patch) {
        Todo todo = find(id);
        LocalDate today = today();
        TodoRequest merged = requestValidator.validate(
            jsonMergePatch.apply(TodoResponse.of(todo, today).toRequest(), patch, TodoRequest.class));
        todo.apply(toValues(merged));
        if (merged.completed() == null) {
            throw ApiException.validation("completed", "완료 여부는 true 또는 false입니다.");
        }
        todo.markCompleted(merged.completed(), LocalDateTime.now(clock).withNano(0));
        todoRepository.flush();
        return TodoResponse.of(todo, today);
    }

    @Transactional
    public void delete(Long id) {
        todoRepository.delete(find(id));
    }

    /** 순서 이동 (D-030). 전체 Todo 순서에서 afterId 바로 뒤로 옮기고 0부터 다시 매긴다 */
    @Transactional
    public TodoResponse move(Long id, Long afterId) {
        Todo todo = find(id);
        List<Todo> reordered = Positions.move(todoRepository.findAllOrdered(), todo, afterId, Todo::getId);
        for (int i = 0; i < reordered.size(); i++) {
            reordered.get(i).changeSortOrder(i);
        }
        todoRepository.flush();
        return TodoResponse.of(todo, today());
    }

    // ------------------------------------------------------------------ 내부

    private Todo find(Long id) {
        return todoRepository.findById(id).orElseThrow(() -> ApiException.notFound("Todo를 찾을 수 없습니다: " + id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static List<TodoResponse> toResponses(List<Todo> todos, List<Long> categoryIds, LocalDate today) {
        return todos.stream()
            .filter(t -> categoryIds == null || categoryIds.isEmpty() || categoryIds.contains(t.getCategoryId()))
            .map(t -> TodoResponse.of(t, today))
            .toList();
    }

    /** 검증(종류별 날짜 규칙·카테고리)과 정리를 마친 값 */
    private Todo.Values toValues(TodoRequest r) {
        LocalDate start = r.startDate();
        LocalDate end = r.endDate();
        switch (r.type()) {
            case DAY -> {
                if (!end.equals(start)) {
                    throw ApiException.validation("endDate", "하루 Todo는 시작일과 마감일이 같아야 합니다.");
                }
            }
            case PERIOD -> {
                if (end.isBefore(start)) {
                    throw ApiException.validation("endDate", "마감일은 시작일과 같거나 뒤여야 합니다.");
                }
            }
            case WEEK -> {
                DayOfWeek weekStart = weekStartDay();
                if (start.getDayOfWeek() != weekStart) {
                    throw ApiException.validation("startDate",
                        "주간 목표의 시작일은 주 시작 요일(" + (weekStart == DayOfWeek.SUNDAY ? "일요일" : "월요일") + ")이어야 합니다.");
                }
                if (!end.equals(start.plusDays(6))) {
                    throw ApiException.validation("endDate", "주간 목표의 마감일은 시작일 + 6일입니다.");
                }
            }
            case MONTH -> {
                if (start.getDayOfMonth() != 1) {
                    throw ApiException.validation("startDate", "월간 목표의 시작일은 그달 1일이어야 합니다.");
                }
                if (!end.equals(start.with(TemporalAdjusters.lastDayOfMonth()))) {
                    throw ApiException.validation("endDate", "월간 목표의 마감일은 그달 말일이어야 합니다.");
                }
            }
        }

        LocalTime startTime = null;
        Integer duration = null;
        if (r.time() != null) {
            startTime = r.time().start().withSecond(0).withNano(0);
            duration = r.time().durationMinutes();
        }

        Long categoryId = r.categoryId();
        if (categoryId == null) {
            categoryId = categoryRepository.getDefault().getId();
        } else if (!categoryRepository.existsById(categoryId)) {
            throw ApiException.validation("categoryId", "없는 카테고리입니다.");
        }

        return new Todo.Values(categoryId, r.title().strip(), r.type(), start, end, startTime, duration, r.color());
    }

    /** 설정의 주 시작 요일 (CAL-04). 설정 API(US-26)가 생기면 그쪽으로 옮긴다 */
    private DayOfWeek weekStartDay() {
        String value = jdbcTemplate.queryForObject("SELECT week_start_day FROM user_settings WHERE setting_id = 1", String.class);
        return "MON".equals(value) ? DayOfWeek.MONDAY : DayOfWeek.SUNDAY;
    }
}
