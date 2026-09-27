package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-24 사용자는 하루 한 개 일기를 쓴다 (09-backlog.md). 오늘 = 2026-09-25
 * <ul>
 *   <li>AC2 펜 아이콘 모양 → FE 담당. 일기를 쓴 날짜 조회만 여기서</li>
 * </ul>
 */
@DisplayName("US-24 일기")
class US24DiaryAcceptanceTest extends AcceptanceTest {

    private static final String URL = "/api/v1/diaries";

    @Test
    @DisplayName("AC1: 날짜당 1개 — 처음 쓰면 새로 생기고(201), 같은 날 다시 쓰면 고쳐진다(200)")
    void onePerDate() throws Exception {
        getJson(URL + "/" + TODAY).andExpect(status().isNotFound());

        putJson(URL + "/" + TODAY, "{\"content\":\"기획서 초안을 절반 썼다.\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.date").value(TODAY))
            .andExpect(jsonPath("$.content").value("기획서 초안을 절반 썼다."))
            .andExpect(jsonPath("$.updatedAt").value("2026-09-25T09:00:00"));
        putJson(URL + "/" + TODAY, "{\"content\":\"기획서 초안을 다 썼다.\"}")
            .andExpect(status().isOk());

        getJson(URL + "/" + TODAY).andExpect(jsonPath("$.content").value("기획서 초안을 다 썼다."));
        getJson(URL + "?from=" + TODAY + "&to=" + TODAY).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("AC1: 삭제할 수 있고, 지운 날에는 다시 쓸 수 있다")
    void delete() throws Exception {
        putJson(URL + "/" + TODAY, "{\"content\":\"첫 일기\"}").andExpect(status().isCreated());

        deleteJson(URL + "/" + TODAY).andExpect(status().isNoContent());
        getJson(URL + "/" + TODAY).andExpect(status().isNotFound());
        deleteJson(URL + "/" + TODAY).andExpect(status().isNotFound());

        putJson(URL + "/" + TODAY, "{\"content\":\"다시 쓴 일기\"}").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC1: 그날 완료한 Todo를 일기 화면에서 함께 볼 수 있다 (DIARY-04, todos?completedOn)")
    void completedTodosOfDay() throws Exception {
        long done = createDayTodo("기획서 초안", TODAY);
        createDayTodo("장보기", TODAY);
        patchJson("/api/v1/todos/" + done, "{\"completed\":true}").andExpect(status().isOk());
        putJson(URL + "/" + TODAY, "{\"content\":\"기획서를 끝냈다.\"}").andExpect(status().isCreated());

        getJson("/api/v1/todos?completedOn=" + TODAY).andExpect(jsonPath("$[*].title", contains("기획서 초안")));
    }

    @Test
    @DisplayName("AC2: 일기를 쓴 날짜를 기간으로 조회한다 (월간 칸 펜 아이콘, 날짜 순)")
    void datesWithDiary() throws Exception {
        putJson(URL + "/2026-09-20", "{\"content\":\"a\"}").andExpect(status().isCreated());
        putJson(URL + "/2026-09-03", "{\"content\":\"b\"}").andExpect(status().isCreated());
        putJson(URL + "/2026-10-02", "{\"content\":\"c\"}").andExpect(status().isCreated());
        putJson(URL + "/2026-08-31", "{\"content\":\"범위 밖\"}").andExpect(status().isCreated());

        getJson(URL + "?from=2026-09-01&to=2026-10-05")
            .andExpect(jsonPath("$[*].date", contains("2026-09-03", "2026-09-20", "2026-10-02")));
    }
}
