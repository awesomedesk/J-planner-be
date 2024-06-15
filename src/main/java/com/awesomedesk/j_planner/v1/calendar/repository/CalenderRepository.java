package com.awesomedesk.j_planner.v1.calendar.repository;

import com.awesomedesk.j_planner.v1.calendar.domain.Calendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface CalenderRepository extends JpaRepository<Calendar, Long> {

    @Query(nativeQuery = true, value =
            "SELECT C.* " +
            "FROM CALENDARS C " +
            "WHERE C.START_DATE_TIME <= :endDateTime " +
                "AND C.END_DATE_TIME >= :startDateTime " +
                "AND C.IS_DELETED = 'N'")
    List<Calendar> findAllByStartDateTimeAndEndDateTime(LocalDateTime startDateTime, LocalDateTime endDateTime);

}
