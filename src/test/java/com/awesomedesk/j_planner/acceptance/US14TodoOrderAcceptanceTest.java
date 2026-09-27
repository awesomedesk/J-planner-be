package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-14 사용자는 Todo 순서를 끌어서 바꾼다 (09-backlog.md)
 * <ul>
 *   <li>AC1 중 "PC 마우스 끌기·모바일 길게 눌러 끌기" → FE 담당. 바뀐 순서 저장·유지만 여기서</li>
 * </ul>
 */
@DisplayName("US-14 Todo 순서 바꾸기")
class US14TodoOrderAcceptanceTest extends AcceptanceTest {

    private static final String URL = "/api/v1/todos";

    @Test
    @DisplayName("AC1: 끌어서 바꾼 순서는 새로고침(다시 조회)해도 유지된다 (D-030)")
    void orderPersists() throws Exception {
        long a = createDayTodo("A", TODAY);
        long b = createDayTodo("B", TODAY);
        long c = createDayTodo("C", TODAY);

        // C를 맨 위로, A를 B 뒤로
        putJson(URL + "/" + c + "/position", "{\"afterId\":null}").andExpect(status().isOk());
        putJson(URL + "/" + a + "/position", "{\"afterId\":" + b + "}").andExpect(status().isOk());

        getJson(URL + "?date=" + TODAY).andExpect(jsonPath("$[*].title", contains("C", "B", "A")));
        getJson(URL + "?date=" + TODAY).andExpect(jsonPath("$[*].title", contains("C", "B", "A")));
    }

    @Test
    @DisplayName("AC1: 다른 날 Todo 뒤로 옮겨도 그날 박스 안의 순서가 맞다 (전체 순서 하나)")
    void orderAcrossDays() throws Exception {
        long a = createDayTodo("A", TODAY);
        long other = createDayTodo("다른 날", "2026-09-26");
        long b = createDayTodo("B", TODAY);

        putJson(URL + "/" + a + "/position", "{\"afterId\":" + b + "}").andExpect(status().isOk());
        getJson(URL + "?date=" + TODAY).andExpect(jsonPath("$[*].title", contains("B", "A")));
        getJson(URL + "?date=2026-09-26").andExpect(jsonPath("$[*].title", contains("다른 날")));
        putJson(URL + "/" + other + "/position", "{\"afterId\":999999}").andExpect(status().isBadRequest());
    }
}
