package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-15 사용자는 Todo에 시간을 정해 시간표에 놓는다 — 타임 블로킹 (09-backlog.md)
 * <ul>
 *   <li>AC1 블록 모양(카테고리 띠·흰 바탕·테두리·체크박스) → FE 담당. 시간표에 올릴 Todo 조회만 여기서</li>
 * </ul>
 * 시간표 조회 = {@code GET /todos?from=&to=&scheduled=true}
 */
@DisplayName("US-15 Todo 타임 블로킹")
class US15TodoTimeBlockAcceptanceTest extends AcceptanceTest {

    private static final String URL = "/api/v1/todos";

    private long timed(String title, String type, String start, String end, String time, int minutes) throws Exception {
        return createTodo("{\"title\":\"" + title + "\",\"type\":\"" + type + "\",\"startDate\":\"" + start
            + "\",\"endDate\":\"" + end + "\",\"time\":{\"start\":\"" + time + "\",\"durationMinutes\":" + minutes + "}}");
    }

    @Test
    @DisplayName("AC1: 시간 지정 Todo만 시간표에 블록으로 (시작 시각·길이). 시간 없는 Todo는 빠진다")
    void onlyTimedTodosInTimetable() throws Exception {
        timed("기획서", "DAY", TODAY, TODAY, "14:00", 90);
        timed("영어 단어", "DAY", TODAY, TODAY, "06:30", 30);
        createDayTodo("장보기", TODAY);

        getJson(URL + "?from=" + TODAY + "&to=" + TODAY + "&scheduled=true")
            .andExpect(jsonPath("$[*].title", contains("영어 단어", "기획서")))
            .andExpect(jsonPath("$[1].time.start").value("14:00"))
            .andExpect(jsonPath("$[1].time.durationMinutes").value(90));
    }

    @Test
    @DisplayName("AC2: 기간·주간·월간 Todo도 시간 지정 가능, 범위 안 어느 날에도 시간표에 나온다 (D-027)")
    void periodTodosEveryDay() throws Exception {
        timed("책 읽기", "PERIOD", "2026-09-24", "2026-09-28", "21:00", 60);
        timed("운동", "WEEK", "2026-09-20", "2026-09-26", "07:00", 40);
        timed("알고리즘", "MONTH", "2026-09-01", "2026-09-30", "22:00", 60);

        getJson(URL + "?from=2026-09-26&to=2026-09-26&scheduled=true")
            .andExpect(jsonPath("$[*].title", contains("알고리즘", "운동", "책 읽기")));
        getJson(URL + "?from=2026-09-28&to=2026-09-28&scheduled=true")
            .andExpect(jsonPath("$[*].title", contains("알고리즘", "책 읽기")));
    }

    @Test
    @DisplayName("AC3: 블록에서 바로 완료 체크 — 시간표 조회에도 완료로 나온다")
    void completeFromBlock() throws Exception {
        long id = timed("기획서", "DAY", TODAY, TODAY, "14:00", 90);
        patchJson(URL + "/" + id, "{\"completed\":true}").andExpect(status().isOk());

        getJson(URL + "?from=" + TODAY + "&to=" + TODAY + "&scheduled=true")
            .andExpect(jsonPath("$[0].completed").value(true));
    }

    @Test
    @DisplayName("시간을 옮기거나 지우면 시간표에 바로 반영된다")
    void moveOrClearTime() throws Exception {
        long id = timed("기획서", "DAY", TODAY, TODAY, "14:00", 90);
        patchJson(URL + "/" + id, "{\"time\":{\"start\":\"16:30\"}}")
            .andExpect(jsonPath("$.time.start").value("16:30"))
            .andExpect(jsonPath("$.time.durationMinutes").value(90));
        patchJson(URL + "/" + id, "{\"time\":null}").andExpect(status().isOk());

        getJson(URL + "?from=" + TODAY + "&to=" + TODAY + "&scheduled=true").andExpect(jsonPath("$.length()").value(0));
    }
}
