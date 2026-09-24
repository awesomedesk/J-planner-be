package com.awesomedesk.j_planner.api.v1.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** US-05 일정 API (08-api-design.md 4절) */
class ScheduleApiTest extends IntegrationTest {

    private ResultActions postSchedule(String json) throws Exception {
        return mvc.perform(post("/api/v1/schedules").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long create(String title, String start, String end) throws Exception {
        String body = postSchedule("{\"title\":\"" + title + "\",\"allDay\":false,\"start\":\"" + start + "\",\"end\":\"" + end + "\"}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private long createCategory(String name) {
        jdbc.update("INSERT INTO categories (name, color, sort_order) VALUES (?, '#2F62A8', 1)", name);
        return jdbc.queryForObject("SELECT category_id FROM categories WHERE name = ?", Long.class, name);
    }

    @Test
    @DisplayName("추가 → 201 + Location, 모든 필드가 돌아온다")
    void createFull() throws Exception {
        long categoryId = createCategory("업무");
        postSchedule("""
            {"title":"팀 미팅","allDay":false,"start":"2026-09-24T10:00:00","end":"2026-09-24T11:00:00",
             "categoryId":%d,"color":"#5B5F97","description":"주간 진행 상황 공유",
             "location":{"name":"회의실 A","latitude":37.5665,"longitude":126.978},
             "url":"https://meet.example.com/team-weekly"}
            """.formatted(categoryId))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("/api/v1/schedules/\\d+")))
            .andExpect(jsonPath("$.title").value("팀 미팅"))
            .andExpect(jsonPath("$.allDay").value(false))
            .andExpect(jsonPath("$.start").value("2026-09-24T10:00:00"))
            .andExpect(jsonPath("$.end").value("2026-09-24T11:00:00"))
            .andExpect(jsonPath("$.categoryId").value(categoryId))
            .andExpect(jsonPath("$.color").value("#5B5F97"))
            .andExpect(jsonPath("$.description").value("주간 진행 상황 공유"))
            .andExpect(jsonPath("$.location.name").value("회의실 A"))
            .andExpect(jsonPath("$.location.latitude").value(37.5665))
            .andExpect(jsonPath("$.location.longitude").value(126.978))
            .andExpect(jsonPath("$.url").value("https://meet.example.com/team-weekly"));
    }

    @Test
    @DisplayName("D-037: 장소는 이름만 저장할 수 있다. 이름을 바꾸며 좌표를 null로 보내면 좌표가 지워진다")
    void locationNameOnly() throws Exception {
        String body = postSchedule("{\"title\":\"a\",\"allDay\":false,\"start\":\"2026-09-24T10:00:00\",\"end\":\"2026-09-24T11:00:00\","
                + "\"location\":{\"name\":\"회의실 A\"}}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.location.name").value("회의실 A"))
            .andExpect(jsonPath("$.location.latitude").value(nullValue()))
            .andExpect(jsonPath("$.location.longitude").value(nullValue()))
            .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));

        mvc.perform(patch("/api/v1/schedules/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":{\"name\":\"강남역\",\"latitude\":37.4979,\"longitude\":127.0276}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.location.latitude").value(37.4979));
        mvc.perform(patch("/api/v1/schedules/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":{\"name\":\"집\",\"latitude\":null,\"longitude\":null}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.location.name").value("집"))
            .andExpect(jsonPath("$.location.latitude").value(nullValue()))
            .andExpect(jsonPath("$.location.longitude").value(nullValue()));
    }

    @Test
    @DisplayName("필수값만 → 카테고리는 미지정, 색·상세는 null")
    void createMinimal() throws Exception {
        long id = create("헬스장", "2026-09-24T18:00:00", "2026-09-24T19:00:00");
        mvc.perform(get("/api/v1/schedules/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryId").value(defaultCategoryId()))
            .andExpect(jsonPath("$.color").value(nullValue()))
            .andExpect(jsonPath("$.description").value(nullValue()))
            .andExpect(jsonPath("$.location").value(nullValue()))
            .andExpect(jsonPath("$.url").value(nullValue()));
    }

    @Test
    @DisplayName("종일 → 시작 00:00:00, 종료 23:59:59로 맞춘다")
    void createAllDay() throws Exception {
        postSchedule("{\"title\":\"휴가\",\"allDay\":true,\"start\":\"2026-09-20T09:30:00\",\"end\":\"2026-09-23T00:00:00\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.start").value("2026-09-20T00:00:00"))
            .andExpect(jsonPath("$.end").value("2026-09-23T23:59:59"));
    }

    @Test
    @DisplayName("입력값 오류 → 400 VALIDATION_FAILED + 필드")
    void createInvalid() throws Exception {
        postSchedule("{\"title\":\"\",\"start\":\"2026-09-24T10:00:00\",\"end\":\"2026-09-24T11:00:00\",\"color\":\"red\",\"url\":\"ftp://x\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder("allDay", "color", "title", "url")));

        postSchedule("{\"title\":\"a\",\"allDay\":false,\"start\":\"2026-09-24T11:00:00\",\"end\":\"2026-09-24T11:00:00\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("end"));

        postSchedule("{\"title\":\"a\",\"allDay\":false,\"start\":\"2026-09-24T10:00:00\",\"end\":\"2026-09-24T11:00:00\",\"categoryId\":9999}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("categoryId"));

        postSchedule("{\"title\":\"a\",\"allDay\":false,\"start\":\"2026-09-24T10:00:00\",\"end\":\"2026-09-24T11:00:00\",\"location\":{\"latitude\":37.5}}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("location"));

        postSchedule("{\"title\":\"a\",\"allDay\":false,\"start\":\"2026-09-24 10:00\",\"end\":\"2026-09-24T11:00:00\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("기간 조회: 겹치는 일정만, 시작 순. 자정에 끝나는 일정은 다음 날에 안 걸림")
    void listOverlapping() throws Exception {
        create("전날 밤샘", "2026-09-23T23:00:00", "2026-09-24T02:00:00");
        create("전날 자정까지", "2026-09-23T22:00:00", "2026-09-24T00:00:00");
        create("오후", "2026-09-24T14:00:00", "2026-09-24T15:00:00");
        create("오전", "2026-09-24T09:00:00", "2026-09-24T10:00:00");
        create("다음 날", "2026-09-25T09:00:00", "2026-09-25T10:00:00");
        postSchedule("{\"title\":\"출장\",\"allDay\":true,\"start\":\"2026-09-22T00:00:00\",\"end\":\"2026-09-26T00:00:00\"}");

        mvc.perform(get("/api/v1/schedules").param("from", "2026-09-24").param("to", "2026-09-24"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].title").value(contains("출장", "전날 밤샘", "오전", "오후")));
    }

    @Test
    @DisplayName("기간 조회: 카테고리 필터 (여러 개)")
    void listByCategory() throws Exception {
        long work = createCategory("업무");
        long study = createCategory("공부");
        postSchedule("{\"title\":\"회의\",\"allDay\":false,\"start\":\"2026-09-24T10:00:00\",\"end\":\"2026-09-24T11:00:00\",\"categoryId\":" + work + "}");
        postSchedule("{\"title\":\"강의\",\"allDay\":false,\"start\":\"2026-09-24T12:00:00\",\"end\":\"2026-09-24T13:00:00\",\"categoryId\":" + study + "}");
        create("미지정 일정", "2026-09-24T14:00:00", "2026-09-24T15:00:00");

        mvc.perform(get("/api/v1/schedules").param("from", "2026-09-01").param("to", "2026-09-30")
                .param("categoryId", String.valueOf(work)).param("categoryId", String.valueOf(defaultCategoryId())))
            .andExpect(jsonPath("$[*].title").value(contains("회의", "미지정 일정")));
    }

    @Test
    @DisplayName("기간 조회 조건 오류 → 400 INVALID_QUERY (없음, 순서 반대, 62일 초과)")
    void listInvalidQuery() throws Exception {
        mvc.perform(get("/api/v1/schedules").param("from", "2026-09-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"))
            .andExpect(jsonPath("$.errors[0].field").value("to"));
        mvc.perform(get("/api/v1/schedules").param("from", "2026-09-10").param("to", "2026-09-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        // 9/1 ~ 11/2 = 63일 → 초과
        mvc.perform(get("/api/v1/schedules").param("from", "2026-09-01").param("to", "2026-11-02"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        // 9/1 ~ 11/1 = 62일 → 된다
        mvc.perform(get("/api/v1/schedules").param("from", "2026-09-01").param("to", "2026-11-01"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH: 보낸 필드만 바뀌고, null은 지운다. 장소는 안쪽 필드도 합친다")
    void patchMerge() throws Exception {
        String body = postSchedule("""
            {"title":"팀 미팅","allDay":false,"start":"2026-09-24T10:00:00","end":"2026-09-24T11:00:00",
             "color":"#5B5F97","location":{"name":"회의실 A","latitude":37.5,"longitude":127.0}}
            """).andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));

        mvc.perform(patch("/api/v1/schedules/" + id).contentType("application/merge-patch+json")
                .content("{\"title\":\"팀 회의\",\"color\":null,\"location\":{\"name\":\"회의실 B\"},\"url\":\"https://a.example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("팀 회의"))
            .andExpect(jsonPath("$.start").value("2026-09-24T10:00:00"))
            .andExpect(jsonPath("$.color").value(nullValue()))
            .andExpect(jsonPath("$.location.name").value("회의실 B"))
            .andExpect(jsonPath("$.location.latitude").value(37.5))
            .andExpect(jsonPath("$.url").value("https://a.example.com"));

        mvc.perform(patch("/api/v1/schedules/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"location\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.location").value(nullValue()));
    }

    @Test
    @DisplayName("PATCH: 종일로 바꾸면 시간 정리, 시간 순서가 틀리면 400, 제목 null은 400")
    void patchRules() throws Exception {
        long id = create("미팅", "2026-09-24T10:00:00", "2026-09-24T11:00:00");
        mvc.perform(patch("/api/v1/schedules/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"allDay\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.start").value("2026-09-24T00:00:00"))
            .andExpect(jsonPath("$.end").value("2026-09-24T23:59:59"));
        mvc.perform(patch("/api/v1/schedules/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"allDay\":false,\"start\":\"2026-09-24T12:00:00\",\"end\":\"2026-09-24T11:00:00\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("end"));
        mvc.perform(patch("/api/v1/schedules/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"title\":null}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("title"));
        mvc.perform(patch("/api/v1/schedules/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryId").value(defaultCategoryId()));
    }

    @Test
    @DisplayName("삭제 → 204, 이후 조회 404, 상세도 삭제 표시, 없는 일정 → 404")
    void deleteSchedule() throws Exception {
        String body = postSchedule("{\"title\":\"a\",\"allDay\":false,\"start\":\"2026-09-24T10:00:00\",\"end\":\"2026-09-24T11:00:00\",\"description\":\"메모\"}")
            .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));

        mvc.perform(delete("/api/v1/schedules/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/schedules/" + id)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/schedules").param("from", "2026-09-24").param("to", "2026-09-24"))
            .andExpect(jsonPath("$", hasSize(0)));
        assertThat(jdbc.queryForObject("SELECT deleted FROM calendars WHERE calendar_id = ?", String.class, id)).isEqualTo("Y");
        assertThat(jdbc.queryForObject("SELECT deleted FROM calendar_details WHERE calendar_id = ?", String.class, id)).isEqualTo("Y");
        mvc.perform(delete("/api/v1/schedules/9999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("상세 값이 모두 비어도 상세 행은 1개 있고, 값을 지웠다 다시 넣어도 된다")
    void detailRowAlwaysExists() throws Exception {
        long id = create("a", "2026-09-24T10:00:00", "2026-09-24T11:00:00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calendar_details WHERE calendar_id = ?", Integer.class, id)).isEqualTo(1);
        mvc.perform(patch("/api/v1/schedules/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"메모\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("메모"));
        mvc.perform(patch("/api/v1/schedules/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"description\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.description").value(nullValue()));
        mvc.perform(patch("/api/v1/schedules/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://x.example.com\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://x.example.com"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calendar_details WHERE calendar_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("카테고리를 지우면 그 일정은 미지정으로")
    void categoryDeletionMovesSchedule() throws Exception {
        long work = createCategory("업무");
        String body = postSchedule("{\"title\":\"회의\",\"allDay\":false,\"start\":\"2026-09-24T10:00:00\",\"end\":\"2026-09-24T11:00:00\",\"categoryId\":" + work + "}")
            .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
        mvc.perform(delete("/api/v1/categories/" + work)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/schedules/" + id)).andExpect(jsonPath("$.categoryId").value(defaultCategoryId()));
    }
}
