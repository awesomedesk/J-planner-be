package com.awesomedesk.j_planner.api.v1.diary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 모든 조회는 회원(userId) 조건을 붙인다 (US-32) */
public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Optional<Diary> findByUserIdAndDate(Long userId, LocalDate date);

    List<Diary> findByUserIdAndDateBetweenOrderByDateAsc(Long userId, LocalDate from, LocalDate to);
}
