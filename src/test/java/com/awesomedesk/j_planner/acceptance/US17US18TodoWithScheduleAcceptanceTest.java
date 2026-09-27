package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-17·18 — 새 API 없이 기존 조회로 처리 (D-041, BE 해당 없음).
 * 화면(목록 배치·안내 문구·입력 창)은 FE 담당이고, 여기서는 FE가 쓰는 조회가 필요한 데이터를 주는지만 확인한다.
 */
@DisplayName("US-17·18 일정과 함께 보는 Todo, 시간 미지정 Todo")
class US17US18TodoWithScheduleAcceptanceTest extends AcceptanceTest {

    @Test
    @DisplayName("US-17 AC1: 그날 일정(시간순)과 시간 지정 Todo를 각각 조회해 함께 보여줄 수 있다 (MO-10)")
    void schedulesAndTimedTodosOfDay() throws Exception {
        createSchedule("점심 약속", "2026-09-25T12:00:00", "2026-09-25T13:00:00", null);
        createSchedule("아침 회의", "2026-09-25T09:30:00", "2026-09-25T10:00:00", null);
        createSchedule("내일 일정", "2026-09-26T09:00:00", "2026-09-26T10:00:00", null);
        createTodo("{\"title\":\"기획서\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\","
            + "\"time\":{\"start\":\"14:00\",\"durationMinutes\":60}}");
        createDayTodo("장보기", TODAY);

        getJson("/api/v1/schedules?from=" + TODAY + "&to=" + TODAY)
            .andExpect(jsonPath("$[*].title", contains("아침 회의", "점심 약속")));
        getJson("/api/v1/todos?from=" + TODAY + "&to=" + TODAY + "&scheduled=true")
            .andExpect(jsonPath("$[*].title", contains("기획서")));
    }

    @Test
    @DisplayName("US-18 AC1: 그날 박스에서 시간 없는 Todo를 셀 수 있고, 시간을 정하면 시간표에 블록으로 나온다")
    void untimedTodoToTimetable() throws Exception {
        long untimed = createDayTodo("장보기", TODAY);
        createDayTodo("청소", TODAY);
        createTodo("{\"title\":\"기획서\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\","
            + "\"time\":{\"start\":\"14:00\",\"durationMinutes\":60}}");

        getJson("/api/v1/todos?date=" + TODAY)
            .andExpect(jsonPath("$[?(@.time == null)].title", contains("장보기", "청소")));

        patchJson("/api/v1/todos/" + untimed, "{\"time\":{\"start\":\"18:00\",\"durationMinutes\":30}}")
            .andExpect(status().isOk());
        getJson("/api/v1/todos?from=" + TODAY + "&to=" + TODAY + "&scheduled=true")
            .andExpect(jsonPath("$[*].title", contains("기획서", "장보기")));
    }
}
