package com.awesomedesk.j_planner.v1.calendar.repository;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.v1.calendar.domain.Calendar;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CalendarRepository extends JpaRepository<Calendar, Long> {

    @Query("SELECT c " +
           "FROM Calendar c " +
           "WHERE c.deleted = false " +
           "  AND c.dateDto.endDateTime >= :startDateTime " +
           "  AND c.dateDto.startDateTime <= :endDateTime " +
           "ORDER BY c.dateDto.startDateTime ASC")
    List<Calendar> findAllByDateRange(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    default List<Calendar> findAllByDateDto(DateDto dateDto) {
        return findAllByDateRange(dateDto.getStartDateTime(), dateDto.getEndDateTime());
    }

}
