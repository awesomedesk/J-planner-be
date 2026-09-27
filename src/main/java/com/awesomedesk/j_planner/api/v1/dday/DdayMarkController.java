package com.awesomedesk.j_planner.api.v1.dday;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** D-Day 달력 표시 API (US-23). 명세: j-planner-product/08-api-design.md 6절 */
@RestController
@RequestMapping("/api/v1/dday-marks")
@RequiredArgsConstructor
public class DdayMarkController {

    private final DdayService ddayService;

    @GetMapping
    public List<DdayMarkResponse> list(
        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return ddayService.marks(from, to);
    }
}
