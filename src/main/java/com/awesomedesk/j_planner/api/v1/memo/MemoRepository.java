package com.awesomedesk.j_planner.api.v1.memo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 모든 조회는 회원(userId) 조건을 붙인다 (US-32) */
public interface MemoRepository extends JpaRepository<Memo, Long> {

    Optional<Memo> findByIdAndUserId(Long id, Long userId);

    /** 최근 수정 순 (D-029) */
    List<Memo> findAllByUserIdOrderByUpdatedAtDescIdDesc(Long userId);
}
