package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-22 사용자는 D-Day를 만들고 목록에서 관리한다 (09-backlog.md). 오늘 = 2026-09-25
 * <ul>
 *   <li>AC3 중 "길게 눌러·끌어서", "모바일 카드 왼쪽으로 밀기" 동작 → FE 담당. 순서 저장·삭제만 여기서</li>
 * </ul>
 */
@DisplayName("US-22 D-Day 관리")
class US22DdayAcceptanceTest extends AcceptanceTest {

    private static final String URL = "/api/v1/ddays";

    private long createDday(String title, String targetDate, String countType) throws Exception {
        return idOf(postJson(URL, "{\"title\":\"" + title + "\",\"targetDate\":\"" + targetDate + "\",\"countType\":\"" + countType + "\"}")
            .andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("AC1: 제목, 날짜, 기준(당일=0일 기본 / 당일=1일), 달력 표시 옵션을 저장한다 (D-012, D-020)")
    void inputs() throws Exception {
        long id = idOf(postJson(URL, """
            {"title":"전역일","targetDate":"2029-06-20",
             "display":{"interval":{"enabled":true,"days":100},"lastDays":{"enabled":true,"days":10},"daily":false}}
            """).andExpect(status().isCreated()));

        getJson(URL + "/" + id)
            .andExpect(jsonPath("$.title").value("전역일"))
            .andExpect(jsonPath("$.targetDate").value("2029-06-20"))
            .andExpect(jsonPath("$.countType").value("COUNTDOWN"))
            .andExpect(jsonPath("$.display.interval.enabled").value(true))
            .andExpect(jsonPath("$.display.interval.days").value(100))
            .andExpect(jsonPath("$.display.lastDays.enabled").value(true))
            .andExpect(jsonPath("$.display.lastDays.days").value(10))
            .andExpect(jsonPath("$.display.daily").value(false))
            .andExpect(jsonPath("$.display.yearly").value(false));

        long up = createDday("운동 시작", "2026-07-01", "COUNTUP");
        getJson(URL + "/" + up).andExpect(jsonPath("$.countType").value("COUNTUP"));
    }

    @Test
    @DisplayName("AC2: 옵션 기본값 — 당일=0일은 목표 날짜만, 당일=1일은 100일 단위 + 매년 (D-020)")
    void defaults() throws Exception {
        long down = createDday("시험", "2026-10-25", "COUNTDOWN");
        getJson(URL + "/" + down)
            .andExpect(jsonPath("$.display.interval.enabled").value(false))
            .andExpect(jsonPath("$.display.lastDays.enabled").value(false))
            .andExpect(jsonPath("$.display.daily").value(false))
            .andExpect(jsonPath("$.display.yearly").value(false));

        long up = createDday("운동 시작", "2026-07-01", "COUNTUP");
        getJson(URL + "/" + up)
            .andExpect(jsonPath("$.display.interval.enabled").value(true))
            .andExpect(jsonPath("$.display.interval.days").value(100))
            .andExpect(jsonPath("$.display.yearly").value(true))
            .andExpect(jsonPath("$.display.lastDays.enabled").value(false))
            .andExpect(jsonPath("$.display.daily").value(false));
    }

    @Test
    @DisplayName("AC1: 기준에 맞지 않는 옵션은 고를 수 없다 (당일=1일 + 매일/마지막 N일, 당일=0일 + 매년 → 400)")
    void optionsByCountType() throws Exception {
        postJson(URL, "{\"title\":\"x\",\"targetDate\":\"2026-07-01\",\"countType\":\"COUNTUP\",\"display\":{\"daily\":true}}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("display.daily"));
        postJson(URL, "{\"title\":\"x\",\"targetDate\":\"2026-10-25\",\"display\":{\"yearly\":true}}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("display.yearly"));
    }

    @Test
    @DisplayName("AC3: 목록은 사용자 순서, 남은 날·지난 날 구분 없이 한 목록, 새 D-Day는 맨 아래 (D-029)")
    void listOrder() throws Exception {
        createDday("연말", "2026-12-31", "COUNTDOWN");
        createDday("이사한 날", "2025-09-25", "COUNTUP");
        createDday("시험", "2026-10-25", "COUNTDOWN");

        getJson(URL)
            .andExpect(jsonPath("$[*].title", contains("연말", "이사한 날", "시험")))
            .andExpect(jsonPath("$[*].today.label", contains("D-97", "D+366", "D-30")));
    }

    @Test
    @DisplayName("AC3: 끌어서 바꾼 순서를 저장한다 (D-030)")
    void reorder() throws Exception {
        long a = createDday("A", "2026-12-31", "COUNTDOWN");
        long b = createDday("B", "2026-12-31", "COUNTDOWN");
        long c = createDday("C", "2026-12-31", "COUNTDOWN");

        putJson(URL + "/" + c + "/position", "{\"afterId\":null}").andExpect(status().isOk());
        putJson(URL + "/" + a + "/position", "{\"afterId\":" + b + "}").andExpect(status().isOk());

        getJson(URL).andExpect(jsonPath("$[*].title", contains("C", "B", "A")));
    }

    @Test
    @DisplayName("AC3: 삭제하면 목록에서 사라진다 (MO-13 밀어서 삭제)")
    void delete() throws Exception {
        long a = createDday("A", "2026-12-31", "COUNTDOWN");
        createDday("B", "2026-12-31", "COUNTDOWN");

        deleteJson(URL + "/" + a).andExpect(status().isNoContent());
        getJson(URL + "/" + a).andExpect(status().isNotFound());
        getJson(URL).andExpect(jsonPath("$[*].title", contains("B")));
    }

    @Test
    @DisplayName("수정: 보낸 값만 바뀌고, 표시 옵션은 안쪽 필드도 보낸 것만 바뀐다")
    void update() throws Exception {
        long id = createDday("연말", "2026-12-31", "COUNTDOWN");

        patchJson(URL + "/" + id, "{\"title\":\"2026 끝\",\"display\":{\"lastDays\":{\"enabled\":true}}}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("2026 끝"))
            .andExpect(jsonPath("$.targetDate").value("2026-12-31"))
            .andExpect(jsonPath("$.display.lastDays.enabled").value(true))
            .andExpect(jsonPath("$.display.lastDays.days").value(7))
            .andExpect(jsonPath("$.display.interval.enabled").value(false));
    }
}
