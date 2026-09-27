package com.awesomedesk.j_planner.api.v1.memo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoRepository extends JpaRepository<Memo, Long> {

    /** 최근 수정 순 (D-029) */
    List<Memo> findAllByOrderByUpdatedAtDescIdDesc();
}
