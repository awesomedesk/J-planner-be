package com.awesomedesk.j_planner.api.v1.diary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Optional<Diary> findByDate(LocalDate date);

    List<Diary> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
