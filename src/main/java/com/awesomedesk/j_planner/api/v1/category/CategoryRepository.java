package com.awesomedesk.j_planner.api.v1.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 미지정 맨 위, 그다음 sort_order 순 (D-029) */
    @Query("select c from Category c order by c.isDefault desc, c.sortOrder asc, c.id asc")
    List<Category> findAllOrdered();

    @Query("select c from Category c where c.isDefault = :flag")
    Optional<Category> findByDefaultFlag(@Param("flag") boolean flag);

    default Category getDefault() {
        return findByDefaultFlag(true)
            .orElseThrow(() -> new IllegalStateException("기본 카테고리(미지정)가 없습니다. schema.sql 기본 데이터를 확인하세요."));
    }

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    @Query("select coalesce(max(c.sortOrder), 0) from Category c")
    int findMaxSortOrder();

    /** 카테고리별 연결된 일정 수 (삭제된 것 제외, D-032). [category_id, count] */
    @Query(value = "SELECT category_id, COUNT(*) FROM calendars WHERE deleted = 'N' GROUP BY category_id", nativeQuery = true)
    List<Object[]> countSchedulesByCategory();

    /** 카테고리별 연결된 Todo 수 (삭제된 것 제외, 완료한 것 포함, D-032). [category_id, count] */
    @Query(value = "SELECT category_id, COUNT(*) FROM todos WHERE deleted = 'N' GROUP BY category_id", nativeQuery = true)
    List<Object[]> countTodosByCategory();

    /** 카테고리 삭제 시 연결된 일정을 미지정으로 (삭제된 일정 포함, D-014) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE calendars SET category_id = :to WHERE category_id = :from", nativeQuery = true)
    int moveSchedules(@Param("from") Long from, @Param("to") Long to);

    /** 카테고리 삭제 시 연결된 Todo를 미지정으로 (삭제·완료된 Todo 포함, D-014) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE todos SET category_id = :to WHERE category_id = :from", nativeQuery = true)
    int moveTodos(@Param("from") Long from, @Param("to") Long to);
}
