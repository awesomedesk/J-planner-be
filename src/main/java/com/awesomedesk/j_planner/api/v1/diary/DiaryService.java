package com.awesomedesk.j_planner.api.v1.diary;

import com.awesomedesk.j_planner.common.api.DateRanges;
import com.awesomedesk.j_planner.common.error.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일기 (US-24, 08-api-design.md 7절). 회원마다 날짜당 1개, 날짜가 곧 주소. 로그인한 회원(userId)의 일기만 (US-32)
 * 그날 완료한 Todo(DIARY-04)는 GET /todos?completedOn=날짜
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryService {

    private final DiaryRepository diaryRepository;

    /** 기간 안의 일기 (월간 펜 아이콘), 날짜 순 */
    public List<DiaryResponse> list(Long userId, LocalDate from, LocalDate to) {
        DateRanges.check(from, to);
        return diaryRepository.findByUserIdAndDateBetweenOrderByDateAsc(userId, from, to).stream().map(DiaryResponse::of).toList();
    }

    public DiaryResponse get(Long userId, LocalDate date) {
        return DiaryResponse.of(find(userId, date));
    }

    /** 쓰기·덮어쓰기 */
    @Transactional
    public Written write(Long userId, LocalDate date, DiaryRequest request) {
        Optional<Diary> existing = diaryRepository.findByUserIdAndDate(userId, date);
        if (existing.isPresent()) {
            Diary diary = existing.get();
            diary.changeContent(request.content());
            diaryRepository.flush();
            return new Written(DiaryResponse.of(diary), false);
        }
        Diary saved = diaryRepository.saveAndFlush(new Diary(userId, date, request.content()));
        return new Written(DiaryResponse.of(saved), true);
    }

    @Transactional
    public void delete(Long userId, LocalDate date) {
        diaryRepository.delete(find(userId, date));
    }

    private Diary find(Long userId, LocalDate date) {
        return diaryRepository.findByUserIdAndDate(userId, date).orElseThrow(() -> ApiException.notFound("그날 쓴 일기가 없습니다: " + date));
    }

    /** created = 새로 썼으면 true (201), 고쳤으면 false (200) */
    public record Written(DiaryResponse diary, boolean created) {
    }
}
