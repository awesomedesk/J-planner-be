package com.awesomedesk.j_planner.api.v1.todo;

import com.awesomedesk.j_planner.common.api.PositionRequest;
import com.awesomedesk.j_planner.common.error.ApiException;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Todo API (US-12~16). 명세: j-planner-product/08-api-design.md 5절 */
@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    /** 조회 조건은 date / from+to(+scheduled) / completedOn 중 하나만 */
    @GetMapping
    public List<TodoResponse> list(
        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date,
        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
        @RequestParam(required = false) Boolean scheduled,
        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate completedOn,
        @RequestParam(name = "categoryId", required = false) List<Long> categoryIds) {

        boolean byRange = from != null || to != null;
        int conditions = (date != null ? 1 : 0) + (byRange ? 1 : 0) + (completedOn != null ? 1 : 0);
        if (conditions != 1) {
            throw ApiException.invalidQuery("조회 조건은 date, from+to, completedOn 중 하나만 보내세요.");
        }
        if (scheduled != null && !byRange) {
            throw ApiException.invalidQuery("scheduled는 from+to와 함께만 쓸 수 있습니다.");
        }
        if (date != null) {
            return todoService.box(date, categoryIds);
        }
        if (completedOn != null) {
            return todoService.completedOn(completedOn, categoryIds);
        }
        if (from == null || to == null) {
            throw ApiException.invalidQuery("from과 to를 함께 보내세요.");
        }
        return todoService.range(from, to, Boolean.TRUE.equals(scheduled), categoryIds);
    }

    @GetMapping("/{id}")
    public TodoResponse get(@PathVariable Long id) {
        return todoService.get(id);
    }

    @PostMapping
    public ResponseEntity<TodoResponse> create(@Valid @RequestBody TodoRequest request) {
        TodoResponse created = todoService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/todos/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public TodoResponse update(@PathVariable Long id, @RequestBody JsonNode patch) {
        return todoService.update(id, patch);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        todoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/position")
    public TodoResponse move(@PathVariable Long id, @RequestBody PositionRequest request) {
        return todoService.move(id, request.afterId());
    }
}
