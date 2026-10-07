package com.awesomedesk.j_planner.api.v1.dday;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 모든 조회는 회원(userId) 조건을 붙인다 (US-32) */
public interface DdayRepository extends JpaRepository<Dday, Long> {

    Optional<Dday> findByIdAndUserId(Long id, Long userId);

    /** 사용자 순서 (남은 날·지난 날 구분 없음, D-029) */
    @Query("select d from Dday d where d.userId = :userId order by d.sortOrder asc, d.id asc")
    List<Dday> findAllOrdered(@Param("userId") Long userId);

    @Query("select coalesce(max(d.sortOrder), -1) from Dday d where d.userId = :userId")
    int findMaxSortOrder(@Param("userId") Long userId);
}
