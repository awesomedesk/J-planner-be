package com.awesomedesk.j_planner.v1.calendar.repository;

import com.awesomedesk.j_planner.v1.calendar.domain.Calendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.awesomedesk.j_planner.common.domain.DateDto;
import java.util.List;

public interface CalendarRepository extends JpaRepository<Calendar, Long> {

    @Query("SELECT c " +
           "FROM Calendar c " +
           "WHERE c.dateDto.startDateTime <= :#{#dateDto.endDateTime} " +
           "  AND c.dateDto.endDateTime >= :#{#dateDto.startDateTime} " +
           "  AND c.deleted = false " +
           "ORDER BY c.dateDto.startDateTime ASC")
    List<Calendar> findAllByDateDto(DateDto dateDto);

}
