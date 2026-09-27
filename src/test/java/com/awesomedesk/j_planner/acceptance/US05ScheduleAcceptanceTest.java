package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * US-05 사용자는 일정을 추가·수정·삭제한다 (09-backlog.md)
 * <ul>
 *   <li>AC1 PC 가운데 창·모바일 전체 화면 → FE 담당</li>
 *   <li>AC3 몸통 Theme2·왼쪽 띠 카테고리 색 → FE 담당. 색 null 저장·카테고리 연결만 여기서</li>
 *   <li>AC4 두 번 눌러 확인·URL 링크 열기 → FE 담당. 삭제·URL 저장만 여기서</li>
 *   <li>AC5 기본 시각 계산 → FE 담당</li>
 * </ul>
 */
@DisplayName("US-05 일정 추가·수정·삭제")
class US05ScheduleAcceptanceTest extends AcceptanceTest {

    private static final String URL = "/api/v1/schedules";

    @Test
    @DisplayName("AC2: 제목, 시작·종료 일시, 카테고리, 일정 색, 장소, URL, 메모를 저장한다")
    void allFieldsSaved() throws Exception {
        long study = createCategory("공부");
        ResultActions created = postJson(URL, """
            {"title":"스터디","allDay":false,"start":"2026-09-25T19:00:00","end":"2026-09-25T21:00:00",
             "categoryId":%d,"color":"#2F62A8","description":"3장까지",
             "location":{"name":"강남역 카페","latitude":37.498,"longitude":127.028},"url":"https://example.com/study"}
            """.formatted(study)).andExpect(status().isCreated());

        getJson(URL + "/" + idOf(created))
            .andExpect(jsonPath("$.title").value("스터디"))
            .andExpect(jsonPath("$.allDay").value(false))
            .andExpect(jsonPath("$.start").value("2026-09-25T19:00:00"))
            .andExpect(jsonPath("$.end").value("2026-09-25T21:00:00"))
            .andExpect(jsonPath("$.categoryId").value(study))
            .andExpect(jsonPath("$.color").value("#2F62A8"))
            .andExpect(jsonPath("$.description").value("3장까지"))
            .andExpect(jsonPath("$.location.name").value("강남역 카페"))
            .andExpect(jsonPath("$.location.latitude").value(37.498))
            .andExpect(jsonPath("$.url").value("https://example.com/study"));
    }

    @Test
    @DisplayName("AC2: 종일 일정은 그날 전체(00:00:00~23:59:59)로 저장한다")
    void allDay() throws Exception {
        postJson(URL, "{\"title\":\"휴가\",\"allDay\":true,\"start\":\"2026-09-28T13:00:00\",\"end\":\"2026-09-30T09:00:00\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.start").value("2026-09-28T00:00:00"))
            .andExpect(jsonPath("$.end").value("2026-09-30T23:59:59"));
    }

    @Test
    @DisplayName("AC2: 카테고리를 고르지 않으면 미지정 (D-014)")
    void defaultCategory() throws Exception {
        postJson(URL, "{\"title\":\"회의\",\"allDay\":false,\"start\":\"2026-09-25T10:00:00\",\"end\":\"2026-09-25T11:00:00\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.categoryId").value(defaultCategoryId()));
    }

    @Test
    @DisplayName("AC3: 색을 고르지 않으면 null로 저장 (FE가 Theme2로 그림, D-030)")
    void colorNull() throws Exception {
        long id = createSchedule("회의", "2026-09-25T10:00:00", "2026-09-25T11:00:00", null);
        getJson(URL + "/" + id).andExpect(jsonPath("$.color").value(nullValue()));
    }

    @Test
    @DisplayName("AC4: 수정 화면에서 수정·삭제할 수 있다. 삭제하면 다시 볼 수 없다")
    void updateAndDelete() throws Exception {
        long id = createSchedule("회의", "2026-09-25T10:00:00", "2026-09-25T11:00:00", null);

        patchJson(URL + "/" + id, "{\"title\":\"주간 회의\",\"url\":\"https://meet.example.com\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("주간 회의"))
            .andExpect(jsonPath("$.url").value("https://meet.example.com"))
            .andExpect(jsonPath("$.start").value("2026-09-25T10:00:00"));

        deleteJson(URL + "/" + id).andExpect(status().isNoContent());
        getJson(URL + "/" + id).andExpect(status().isNotFound());
        getJson(URL + "?from=2026-09-25&to=2026-09-25").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("AC6: 장소는 이름만 저장 가능. 이름을 바꾸면 좌표는 지움 — FE가 null로 보냄 (D-037, D-040)")
    void locationNameOnly() throws Exception {
        ResultActions created = postJson(URL, """
            {"title":"점심","allDay":false,"start":"2026-09-25T12:00:00","end":"2026-09-25T13:00:00",
             "location":{"name":"회사 앞 식당"}}
            """).andExpect(status().isCreated())
            .andExpect(jsonPath("$.location.name").value("회사 앞 식당"))
            .andExpect(jsonPath("$.location.latitude").value(nullValue()));
        long id = idOf(created);

        patchJson(URL + "/" + id, "{\"location\":{\"name\":\"강남역\",\"latitude\":37.498,\"longitude\":127.028}}")
            .andExpect(jsonPath("$.location.latitude").value(37.498));
        patchJson(URL + "/" + id, "{\"location\":{\"name\":\"역삼역\",\"latitude\":null,\"longitude\":null}}")
            .andExpect(jsonPath("$.location.name").value("역삼역"))
            .andExpect(jsonPath("$.location.latitude").value(nullValue()))
            .andExpect(jsonPath("$.location.longitude").value(nullValue()));
    }
}
