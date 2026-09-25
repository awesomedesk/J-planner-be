package com.awesomedesk.j_planner.api.v1.todo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record TodoResponse(
    Long id,
    String title,
    TodoType type,
    LocalDate startDate,
    LocalDate endDate,
    Time time,
    Long categoryId,
    String color,
    boolean completed,
    LocalDateTime completedAt,
    int sortOrder,
    boolean overdue
) {

    public record Time(@JsonFormat(pattern = "HH:mm") LocalTime start, int durationMinutes) {
    }

    static TodoResponse of(Todo t, LocalDate today) {
        Time time = t.getStartTime() == null ? null : new Time(t.getStartTime(), t.getDurationMinutes());
        return new TodoResponse(t.getId(), t.getTitle(), t.getType(), t.getStartDate(), t.getEndDate(), time,
            t.getCategoryId(), t.getColor(), t.isCompleted(), t.getCompletedAt(), t.getSortOrder(), t.isOverdue(today));
    }

    /** PATCH 합치기용: 현재 값을 요청 모양으로 */
    TodoRequest toRequest() {
        TodoRequest.Time reqTime = time == null ? null : new TodoRequest.Time(time.start(), time.durationMinutes());
        return new TodoRequest(title, type, startDate, endDate, reqTime, categoryId, color, completed);
    }
}
