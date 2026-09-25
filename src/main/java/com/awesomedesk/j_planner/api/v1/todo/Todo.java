package com.awesomedesk.j_planner.api.v1.todo;

import com.awesomedesk.j_planner.common.converter.attribute.BooleanToStringConverter;
import com.awesomedesk.j_planner.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Todo (07-db-design.md 4-4). 모든 종류를 start_date ~ end_date 하나로 표현한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "todos")
@SQLDelete(sql = "UPDATE todos SET deleted = 'Y', deleted_at = NOW() WHERE todo_id = ?")
@SQLRestriction("deleted = 'N'")
public class Todo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "todo_id")
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "todo_type", nullable = false, length = 10)
    private TodoType type;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** null = 시간 지정 안 함 (목록에만) */
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** null = 테마 Theme2 (D-030) */
    @Column(length = 7)
    private String color;

    @Column(nullable = false, length = 1)
    @Convert(converter = BooleanToStringConverter.class)
    private boolean completed;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Todo(Values v, int sortOrder) {
        apply(v);
        this.completed = false;
        this.sortOrder = sortOrder;
    }

    public void apply(Values v) {
        this.categoryId = v.categoryId();
        this.title = v.title();
        this.type = v.type();
        this.startDate = v.startDate();
        this.endDate = v.endDate();
        this.startTime = v.startTime();
        this.durationMinutes = v.durationMinutes();
        this.color = v.color();
    }

    /** 완료 표시. 이미 같은 상태면 완료 시각을 바꾸지 않는다 */
    public void markCompleted(boolean completed, LocalDateTime now) {
        if (this.completed == completed) {
            return;
        }
        this.completed = completed;
        this.completedAt = completed ? now : null;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 지난 미완료 (TODO-13) */
    public boolean isOverdue(LocalDate today) {
        return !completed && endDate.isBefore(today);
    }

    /** 검증·정리가 끝난 값 */
    public record Values(Long categoryId, String title, TodoType type, LocalDate startDate, LocalDate endDate,
                         LocalTime startTime, Integer durationMinutes, String color) {
    }
}
