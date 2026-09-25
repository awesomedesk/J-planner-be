package com.awesomedesk.j_planner.api.v1.todo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    /** 그날 박스: 그날에 해당하는 Todo (+ includeOverdue면 지난 미완료도). 미완료 먼저, 사용자 순서 (D-015, D-029) */
    @Query("select t from Todo t where (t.startDate <= :date and t.endDate >= :date) "
        + "or (:includeOverdue = true and t.completed = false and t.endDate < :date) "
        + "order by t.completed asc, t.sortOrder asc, t.id asc")
    List<Todo> findBox(@Param("date") LocalDate date, @Param("includeOverdue") boolean includeOverdue);

    /** 기간과 겹치는 Todo */
    @Query("select t from Todo t where t.startDate <= :to and t.endDate >= :from "
        + "order by t.startDate asc, t.startTime asc nulls last, t.id asc")
    List<Todo> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 기간과 겹치고 시간이 있는 Todo (시간표 블록) */
    @Query("select t from Todo t where t.startDate <= :to and t.endDate >= :from and t.startTime is not null "
        + "order by t.startDate asc, t.startTime asc, t.id asc")
    List<Todo> findScheduled(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 그날 완료한 Todo (DIARY-04) */
    @Query("select t from Todo t where t.completedAt >= :start and t.completedAt < :end order by t.completedAt asc, t.id asc")
    List<Todo> findCompletedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select t from Todo t order by t.sortOrder asc, t.id asc")
    List<Todo> findAllOrdered();

    @Query("select coalesce(max(t.sortOrder), -1) from Todo t")
    int findMaxSortOrder();
}
