package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-16 사용자는 지난 미완료 Todo를 놓치지 않는다 (09-backlog.md). 오늘 = 2026-09-25
 * <ul>
 *   <li>AC1 빨간 ! 와 'n/n 지남' 표시 → FE 담당 (overdue 표시로 센다)</li>
 * </ul>
 */
@DisplayName("US-16 지난 미완료 Todo")
class US16OverdueTodoAcceptanceTest extends AcceptanceTest {

    private static final String URL = "/api/v1/todos";

    @Test
    @DisplayName("AC1: 날짜가 지난 미완료 Todo는 원래 날짜 박스와 오늘 박스 모두에 overdue로 (D-029)")
    void overdueInOriginalAndToday() throws Exception {
        createDayTodo("어제 할 일", "2026-09-24");
        createTodo("지난 기간", "PERIOD", "2026-09-20", "2026-09-23");
        createDayTodo("오늘 할 일", TODAY);

        getJson(URL + "?date=2026-09-24")
            .andExpect(jsonPath("$[*].title", contains("어제 할 일")))
            .andExpect(jsonPath("$[0].overdue").value(true));
        getJson(URL + "?date=" + TODAY)
            .andExpect(jsonPath("$[*].title", contains("어제 할 일", "지난 기간", "오늘 할 일")))
            .andExpect(jsonPath("$[*].overdue", contains(true, true, false)));
        // 오늘이 아닌 다른 날 박스에는 끌려오지 않는다
        getJson(URL + "?date=2026-09-26").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("AC1: 완료했으면 지난 날짜여도 경고 없음, 오늘 박스에도 안 나온다")
    void completedIsNotOverdue() throws Exception {
        long id = createDayTodo("어제 할 일", "2026-09-24");
        patchJson(URL + "/" + id, "{\"completed\":true}").andExpect(status().isOk());

        getJson(URL + "?date=2026-09-24").andExpect(jsonPath("$[0].overdue").value(false));
        getJson(URL + "?date=" + TODAY).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("AC2: 자동으로 날짜가 바뀌지 않는다. 사용자가 날짜를 바꾸면 경고가 사라진다 (D-015)")
    void noAutoMoveAndDateChangeClears() throws Exception {
        long id = createDayTodo("어제 할 일", "2026-09-24");
        getJson(URL + "?date=" + TODAY).andExpect(status().isOk());

        getJson(URL + "/" + id)
            .andExpect(jsonPath("$.startDate").value("2026-09-24"))
            .andExpect(jsonPath("$.overdue").value(true));
        patchJson(URL + "/" + id, "{\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\"}")
            .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    @DisplayName("AC2: 오늘 박스에서 지난 미완료의 순서는 제자리 (사용자 순서 그대로, 위로 끌어올리지 않음)")
    void overdueKeepsItsPlace() throws Exception {
        createDayTodo("오늘 A", TODAY);
        createDayTodo("어제 할 일", "2026-09-24");
        createDayTodo("오늘 B", TODAY);

        getJson(URL + "?date=" + TODAY)
            .andExpect(jsonPath("$[*].title", contains("오늘 A", "어제 할 일", "오늘 B")));
    }
}
