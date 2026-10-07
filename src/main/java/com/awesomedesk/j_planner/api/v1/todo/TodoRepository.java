package com.awesomedesk.j_planner.api.v1.todo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 모든 조회는 회원(userId) 조건을 붙인다 (US-32).
 * 조회 메서드의 카테고리 조건: allCategories = true면 전체, 아니면 categoryIds 안의 것만 (US-11 필터).
 * 빈 목록으로 IN ()이 되지 않게 전체일 때도 자리 채움 값을 넘긴다 ({@link TodoService}).
 */
public interface TodoRepository extends JpaRepository<Todo, Long> {

    Optional<Todo> findByIdAndUserId(Long id, Long userId);

    /** 그날 박스: 그날에 해당하는 Todo (+ includeOverdue면 지난 미완료도). 미완료 먼저, 사용자 순서 (D-015, D-029) */
    @Query("select t from Todo t where t.userId = :userId and ((t.startDate <= :date and t.endDate >= :date) "
        + "or (:includeOverdue = true and t.completed = false and t.endDate < :date))"
        + " and (:allCategories = true or t.categoryId in :categoryIds) "
        + "order by t.completed asc, t.sortOrder asc, t.id asc")
    List<Todo> findBox(@Param("userId") Long userId, @Param("date") LocalDate date, @Param("includeOverdue") boolean includeOverdue,
                       @Param("allCategories") boolean allCategories, @Param("categoryIds") List<Long> categoryIds);

    /** 기간과 겹치는 Todo */
    @Query("select t from Todo t where t.userId = :userId and t.startDate <= :to and t.endDate >= :from "
        + "and (:allCategories = true or t.categoryId in :categoryIds) "
        + "order by t.startDate asc, t.startTime asc nulls last, t.id asc")
    List<Todo> findOverlapping(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to,
                               @Param("allCategories") boolean allCategories, @Param("categoryIds") List<Long> categoryIds);

    /** 기간과 겹치고 시간이 있는 Todo (시간표 블록) */
    @Query("select t from Todo t where t.userId = :userId and t.startDate <= :to and t.endDate >= :from and t.startTime is not null "
        + "and (:allCategories = true or t.categoryId in :categoryIds) "
        + "order by t.startDate asc, t.startTime asc, t.id asc")
    List<Todo> findScheduled(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to,
                             @Param("allCategories") boolean allCategories, @Param("categoryIds") List<Long> categoryIds);

    /** 그날 완료한 Todo (DIARY-04) */
    @Query("select t from Todo t where t.userId = :userId and t.completedAt >= :start and t.completedAt < :end "
        + "and (:allCategories = true or t.categoryId in :categoryIds) "
        + "order by t.completedAt asc, t.id asc")
    List<Todo> findCompletedBetween(@Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                    @Param("allCategories") boolean allCategories, @Param("categoryIds") List<Long> categoryIds);

    @Query("select t from Todo t where t.userId = :userId order by t.sortOrder asc, t.id asc")
    List<Todo> findAllOrdered(@Param("userId") Long userId);

    @Query("select coalesce(max(t.sortOrder), -1) from Todo t where t.userId = :userId")
    int findMaxSortOrder(@Param("userId") Long userId);
}
