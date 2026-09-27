package com.awesomedesk.j_planner.api.v1.dday;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DdayRepository extends JpaRepository<Dday, Long> {

    /** 사용자 순서 (남은 날·지난 날 구분 없음, D-029) */
    @Query("select d from Dday d order by d.sortOrder asc, d.id asc")
    List<Dday> findAllOrdered();

    @Query("select coalesce(max(d.sortOrder), -1) from Dday d")
    int findMaxSortOrder();
}
