package com.awesomedesk.j_planner.api.v1.schedule;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 일정 API (US-05). 명세: j-planner-product/08-api-design.md 4절 */
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    public List<ScheduleResponse> list(
        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
        @RequestParam(name = "categoryId", required = false) List<Long> categoryIds) {
        return scheduleService.list(from, to, categoryIds);
    }

    @GetMapping("/{id}")
    public ScheduleResponse get(@PathVariable Long id) {
        return scheduleService.get(id);
    }

    @PostMapping
    public ResponseEntity<ScheduleResponse> create(@Valid @RequestBody ScheduleRequest request) {
        ScheduleResponse created = scheduleService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/schedules/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public ScheduleResponse update(@PathVariable Long id, @RequestBody JsonNode patch) {
        return scheduleService.update(id, patch);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
