package com.awesomedesk.j_planner.api.v1.diary;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 일기 API (US-24). 명세: j-planner-product/08-api-design.md 7절 */
@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @GetMapping
    public List<DiaryResponse> list(
        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return diaryService.list(from, to);
    }

    @GetMapping("/{date}")
    public DiaryResponse get(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return diaryService.get(date);
    }

    /** 새로 쓰면 201, 고치면 200 */
    @PutMapping("/{date}")
    public ResponseEntity<DiaryResponse> write(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date,
                                               @Valid @RequestBody DiaryRequest request) {
        DiaryService.Written written = diaryService.write(date, request);
        if (written.created()) {
            return ResponseEntity.created(URI.create("/api/v1/diaries/" + date)).body(written.diary());
        }
        return ResponseEntity.ok(written.diary());
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> delete(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        diaryService.delete(date);
        return ResponseEntity.noContent().build();
    }
}
