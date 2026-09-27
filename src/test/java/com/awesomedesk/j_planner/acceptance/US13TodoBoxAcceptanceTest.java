package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-13 사용자는 그날의 Todo를 한 박스에서 보고 완료한다 (09-backlog.md)
 * <ul>
 *   <li>AC2 중 "숨김·'완료 n개 보기'로 펼침" → FE 담당. 완료 상태와 순서(미완료 먼저)만 여기서</li>
 * </ul>
 */
@DisplayName("US-13 Todo 박스와 완료")
class US13TodoBoxAcceptanceTest extends AcceptanceTest {

    private static final String URL = "/api/v1/todos";

    @Test
    @DisplayName("AC1: 하루·기간·주간·월간을 나누지 않고 한 박스, 종류는 꼬리표(type)로만 구분 (D-013)")
    void oneBox() throws Exception {
        createTodo("월간", "MONTH", "2026-09-01", "2026-09-30");
        createTodo("하루", "DAY", "2026-09-25", "2026-09-25");
        createTodo("주간", "WEEK", "2026-09-20", "2026-09-26");
        createTodo("기간", "PERIOD", "2026-09-23", "2026-09-27");

        getJson(URL + "?date=" + TODAY)
            .andExpect(jsonPath("$[*].title", contains("월간", "하루", "주간", "기간")))
            .andExpect(jsonPath("$[*].type", contains("MONTH", "DAY", "WEEK", "PERIOD")));
    }

    @Test
    @DisplayName("AC2: 체크하면 완료, 박스에서는 미완료 뒤로 간다 (FE가 숨기고 '완료 n개 보기') (D-015)")
    void completeMovesAfterIncomplete() throws Exception {
        long a = createDayTodo("A", TODAY);
        createDayTodo("B", TODAY);
        createDayTodo("C", TODAY);

        patchJson(URL + "/" + a, "{\"completed\":true}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.completedAt").value("2026-09-25T09:00:00"));

        getJson(URL + "?date=" + TODAY)
            .andExpect(jsonPath("$[*].title", contains("B", "C", "A")))
            .andExpect(jsonPath("$[*].completed", contains(false, false, true)));

        // 체크를 풀면 원래 자리로
        patchJson(URL + "/" + a, "{\"completed\":false}").andExpect(jsonPath("$.completedAt").value(nullValue()));
        getJson(URL + "?date=" + TODAY).andExpect(jsonPath("$[*].title", contains("A", "B", "C")));
    }

    @Test
    @DisplayName("AC2: 기간·주간·월간도 한 번 체크 = 전체 완료. 기간 안 다른 날 박스에서도 완료 (D-027)")
    void periodCompletesOnce() throws Exception {
        long period = createTodo("책 읽기", "PERIOD", "2026-09-25", "2026-09-28");
        long week = createTodo("운동 3회", "WEEK", "2026-09-20", "2026-09-26");
        long month = createTodo("알고리즘", "MONTH", "2026-09-01", "2026-09-30");
        for (long id : new long[] {period, week, month}) {
            patchJson(URL + "/" + id, "{\"completed\":true}").andExpect(status().isOk());
        }

        getJson(URL + "?date=2026-09-26")
            .andExpect(jsonPath("$[*].completed", contains(true, true, true)));
    }

    @Test
    @DisplayName("D-041: 완료 여부는 true/false만. null은 400")
    void completedNullRejected() throws Exception {
        long id = createDayTodo("A", TODAY);
        patchJson(URL + "/" + id, "{\"completed\":null}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("completed"));
    }
}
