package com.awesomedesk.j_planner.api.v1.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 모든 조회는 회원(userId) 조건을 붙인다 (US-32). 남의 카테고리는 없는 것과 같다 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 미지정 맨 위, 그다음 sort_order 순 (D-029) */
    @Query("select c from Category c where c.userId = :userId order by c.isDefault desc, c.sortOrder asc, c.id asc")
    List<Category> findAllOrdered(@Param("userId") Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Query("select c from Category c where c.userId = :userId and c.isDefault = true")
    Optional<Category> findDefault(@Param("userId") Long userId);

    default Category getDefault(Long userId) {
        return findDefault(userId)
            .orElseThrow(() -> new IllegalStateException("회원의 기본 카테고리(미지정)가 없습니다: user_id " + userId));
    }

    boolean existsByUserIdAndName(Long userId, String name);

    boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);

    @Query("select coalesce(max(c.sortOrder), 0) from Category c where c.userId = :userId")
    int findMaxSortOrder(@Param("userId") Long userId);

    /** 카테고리별 연결된 일정 수 (삭제된 것 제외, D-032). [category_id, count] */
    @Query(value = "SELECT category_id, COUNT(*) FROM calendars WHERE user_id = :userId AND deleted = 'N' GROUP BY category_id",
        nativeQuery = true)
    List<Object[]> countSchedulesByCategory(@Param("userId") Long userId);

    /** 카테고리별 연결된 Todo 수 (삭제된 것 제외, 완료한 것 포함, D-032). [category_id, count] */
    @Query(value = "SELECT category_id, COUNT(*) FROM todos WHERE user_id = :userId AND deleted = 'N' GROUP BY category_id",
        nativeQuery = true)
    List<Object[]> countTodosByCategory(@Param("userId") Long userId);

    /** 카테고리 삭제 시 연결된 일정을 미지정으로 (삭제된 일정 포함, D-014). 카테고리가 회원 것이라 연결된 일정도 그 회원 것 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE calendars SET category_id = :to WHERE category_id = :from", nativeQuery = true)
    int moveSchedules(@Param("from") Long from, @Param("to") Long to);

    /** 카테고리 삭제 시 연결된 Todo를 미지정으로 (삭제·완료된 Todo 포함, D-014) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE todos SET category_id = :to WHERE category_id = :from", nativeQuery = true)
    int moveTodos(@Param("from") Long from, @Param("to") Long to);
}
