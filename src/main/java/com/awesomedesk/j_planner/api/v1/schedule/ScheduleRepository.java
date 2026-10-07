package com.awesomedesk.j_planner.api.v1.schedule;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 모든 조회는 회원(userId) 조건을 붙인다 (US-32) */
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findByIdAndUserId(Long id, Long userId);

    /** [start, end) 구간과 겹치는 일정. 자정에 끝나는 일정은 다음 날에 걸치지 않는다. */
    @Query("select s from Schedule s where s.userId = :userId and s.startDateTime < :end and s.endDateTime > :start "
        + "order by s.startDateTime, s.id")
    List<Schedule> findOverlapping(@Param("userId") Long userId, @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    @Query("select s from Schedule s where s.userId = :userId and s.startDateTime < :end and s.endDateTime > :start "
        + "and s.categoryId in :categoryIds order by s.startDateTime, s.id")
    List<Schedule> findOverlapping(@Param("userId") Long userId, @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end, @Param("categoryIds") Collection<Long> categoryIds);
}
