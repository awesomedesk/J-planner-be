package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-23 사용자는 달력에서 D-Day를 본다 (09-backlog.md). 오늘(등록일) = 2026-09-25
 * 계산 규칙의 세부 경우는 DdayMarkCalculatorTest. 여기서는 API로 AC를 확인한다.
 * <ul>
 *   <li>칸 안의 모양·위치 → FE 담당</li>
 * </ul>
 */
@DisplayName("US-23 달력의 D-Day 표시")
class US23DdayMarksAcceptanceTest extends AcceptanceTest {

    private static final String MARKS = "/api/v1/dday-marks";

    private long dday(String json) throws Exception {
        return idOf(postJson("/api/v1/ddays", json).andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("AC1: 목표 날짜 칸 항상 + 고른 옵션에 해당하는 날. 글자: D-Day, D-900식, 당일=1일 D+1·100일·1주년 (D-020, D-032)")
    void targetAndOptions() throws Exception {
        dday("{\"title\":\"시험\",\"targetDate\":\"2026-10-20\"}");
        dday("{\"title\":\"연말\",\"targetDate\":\"2026-12-29\",\"display\":{\"interval\":{\"enabled\":true,\"days\":90}}}");
        dday("{\"title\":\"운동 시작\",\"targetDate\":\"2026-07-01\",\"countType\":\"COUNTUP\"}");
        dday("{\"title\":\"이사한 날\",\"targetDate\":\"2025-10-15\",\"countType\":\"COUNTUP\"}");
        dday("{\"title\":\"블로그\",\"targetDate\":\"2026-10-31\",\"countType\":\"COUNTUP\"}");

        getJson(MARKS + "?from=2026-09-01&to=2026-10-31")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].date", contains("2026-09-30", "2026-10-08", "2026-10-15", "2026-10-20", "2026-10-31")))
            .andExpect(jsonPath("$[*].label", contains("D-90", "100일", "1주년", "D-Day", "D+1")))
            .andExpect(jsonPath("$[*].kind", contains("INTERVAL", "INTERVAL", "YEARLY", "TARGET", "TARGET")));
    }

    @Test
    @DisplayName("AC2: 한 D-Day가 같은 날 여러 옵션에 걸리면 하나만, 서로 다른 D-Day는 모두 (D-Day 순서대로)")
    void oneMarkPerDdayPerDate() throws Exception {
        long exam = dday("{\"title\":\"시험\",\"targetDate\":\"2026-10-02\","
            + "\"display\":{\"interval\":{\"enabled\":true,\"days\":7},\"lastDays\":{\"enabled\":true,\"days\":7},\"daily\":true}}");
        long trip = dday("{\"title\":\"여행\",\"targetDate\":\"2026-09-25\"}");

        // 9/25: 시험은 D-7 (N일 단위·마지막 N일·매일이 겹침 → N일 단위 하나), 여행은 D-Day
        getJson(MARKS + "?from=2026-09-25&to=2026-09-25")
            .andExpect(jsonPath("$[*].ddayId", contains((int) exam, (int) trip)))
            .andExpect(jsonPath("$[*].kind", contains("INTERVAL", "TARGET")))
            .andExpect(jsonPath("$[*].label", contains("D-7", "D-Day")));

        // D-Day 순서를 바꾸면 같은 날 표시 순서도 바뀐다
        putJson("/api/v1/ddays/" + trip + "/position", "{\"afterId\":null}").andExpect(status().isOk());
        getJson(MARKS + "?from=2026-09-25&to=2026-09-25").andExpect(jsonPath("$[*].ddayId", contains((int) trip, (int) exam)));
    }

    @Test
    @DisplayName("AC3: 당일=0일의 N일 단위·매일은 목표일까지만, 마지막 N일은 D-7~D-1 + 목표일 D-Day (D-043)")
    void countdownEndsAtTarget() throws Exception {
        dday("{\"title\":\"여행\",\"targetDate\":\"2026-10-05\","
            + "\"display\":{\"interval\":{\"enabled\":true,\"days\":5},\"lastDays\":{\"enabled\":true,\"days\":7},\"daily\":true}}");

        getJson(MARKS + "?from=2026-09-24&to=2026-10-31")
            .andExpect(jsonPath("$[0].date").value("2026-09-25"))
            .andExpect(jsonPath("$[*].label", contains(
                "D-10", "D-9", "D-8", "D-7", "D-6", "D-5", "D-4", "D-3", "D-2", "D-1", "D-Day")))
            .andExpect(jsonPath("$[*].kind", contains(
                "INTERVAL", "DAILY", "DAILY", "LAST_DAYS", "LAST_DAYS", "INTERVAL", "LAST_DAYS", "LAST_DAYS", "LAST_DAYS", "LAST_DAYS", "TARGET")));
    }

    @Test
    @DisplayName("AC3: 매년 — 2월 29일 목표일은 평년 2월 28일에 (D-043)")
    void leapDayAnniversary() throws Exception {
        dday("{\"title\":\"결혼\",\"targetDate\":\"2024-02-29\",\"countType\":\"COUNTUP\",\"display\":{\"interval\":{\"enabled\":false}}}");

        getJson(MARKS + "?from=2027-02-01&to=2027-03-31")
            .andExpect(jsonPath("$[*].date", contains("2027-02-28")))
            .andExpect(jsonPath("$[*].label", contains("3주년")));
    }

    @Test
    @DisplayName("조회 기간은 최대 62일, from·to 필수 (D-032)")
    void queryRange() throws Exception {
        getJson(MARKS + "?from=2026-09-01&to=2026-11-02").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        getJson(MARKS + "?from=2026-09-01").andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        getJson(MARKS + "?from=2026-09-01&to=2026-11-01").andExpect(status().isOk());
    }
}
