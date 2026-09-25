package com.awesomedesk.j_planner.api.v1.todo;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** US-12~16 Todo API (08-api-design.md 5절). 오늘 = 2026-09-25(금), 주 시작 = 일요일 */
class TodoApiTest extends IntegrationTest {

    private ResultActions postTodo(String json) throws Exception {
        return mvc.perform(post("/api/v1/todos").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long create(String title, String type, String start, String end) throws Exception {
        String body = postTodo("{\"title\":\"" + title + "\",\"type\":\"" + type + "\",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private ResultActions patchTodo(long id, String json) throws Exception {
        return mvc.perform(patch("/api/v1/todos/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ------------------------------------------------------------------ 추가 (US-12)

    @Test
    @DisplayName("추가 → 201 + Location, 미완료·미지정·맨 뒤, 시간 HH:mm")
    void createFull() throws Exception {
        create("첫 번째", "DAY", "2026-09-25", "2026-09-25");
        postTodo("""
            {"title":" 기획서 초안 ","type":"DAY","startDate":"2026-09-25","endDate":"2026-09-25",
             "time":{"start":"11:00","durationMinutes":90},"color":"#2F62A8","completed":true}
            """)
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("/api/v1/todos/\\d+")))
            .andExpect(jsonPath("$.title").value("기획서 초안"))
            .andExpect(jsonPath("$.type").value("DAY"))
            .andExpect(jsonPath("$.time.start").value("11:00"))
            .andExpect(jsonPath("$.time.durationMinutes").value(90))
            .andExpect(jsonPath("$.categoryId").value(defaultCategoryId()))
            .andExpect(jsonPath("$.color").value("#2F62A8"))
            .andExpect(jsonPath("$.completed").value(false))
            .andExpect(jsonPath("$.completedAt").value(nullValue()))
            .andExpect(jsonPath("$.sortOrder").value(1))
            .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    @DisplayName("종류별 날짜 규칙: 맞으면 201 (하루·기간·주간 일~토·월간 1일~말일)")
    void typeRulesOk() throws Exception {
        create("하루", "DAY", "2026-09-25", "2026-09-25");
        create("기간", "PERIOD", "2026-09-24", "2026-09-30");
        create("주간", "WEEK", "2026-09-20", "2026-09-26");
        create("월간", "MONTH", "2026-09-01", "2026-09-30");
        create("2월", "MONTH", "2027-02-01", "2027-02-28");
    }

    @Test
    @DisplayName("종류별 날짜 규칙 위반 → 400 VALIDATION_FAILED + 필드")
    void typeRulesViolated() throws Exception {
        String[][] bad = {
            {"DAY", "2026-09-25", "2026-09-26", "endDate"},
            {"PERIOD", "2026-09-25", "2026-09-24", "endDate"},
            {"WEEK", "2026-09-21", "2026-09-27", "startDate"},   // 월요일 시작 (설정은 일요일)
            {"WEEK", "2026-09-20", "2026-09-25", "endDate"},
            {"MONTH", "2026-09-02", "2026-09-30", "startDate"},
            {"MONTH", "2026-09-01", "2026-10-01", "endDate"},
        };
        for (String[] b : bad) {
            postTodo("{\"title\":\"x\",\"type\":\"" + b[0] + "\",\"startDate\":\"" + b[1] + "\",\"endDate\":\"" + b[2] + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value(b[3]));
        }
    }

    @Test
    @DisplayName("주간 목표는 설정의 주 시작 요일을 따른다 (월요일이면 월~일)")
    void weekFollowsSetting() throws Exception {
        jdbc.update("UPDATE user_settings SET week_start_day = 'MON' WHERE setting_id = 1");
        create("주간", "WEEK", "2026-09-21", "2026-09-27");
        postTodo("{\"title\":\"x\",\"type\":\"WEEK\",\"startDate\":\"2026-09-20\",\"endDate\":\"2026-09-26\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("startDate"));
    }

    @Test
    @DisplayName("입력값 오류: 필수값·시간 길이·색·없는 카테고리·시간 형식")
    void createInvalid() throws Exception {
        postTodo("{\"title\":\"\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"color\":\"red\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder("color", "title", "type")));
        postTodo("{\"title\":\"x\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"time\":{\"start\":\"11:00\",\"durationMinutes\":0}}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("time.durationMinutes"));
        postTodo("{\"title\":\"x\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"time\":{\"start\":\"11:00\"}}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("time.durationMinutes"));
        postTodo("{\"title\":\"x\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"categoryId\":9999}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
        postTodo("{\"title\":\"x\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"time\":{\"start\":\"11시\",\"durationMinutes\":30}}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        postTodo("{\"title\":\"x\",\"type\":\"YEAR\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\"}")
            .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 박스 (US-13, 16)

    @Test
    @DisplayName("그날 박스: 하루·기간·주간·월간을 한 박스에, 미완료 먼저 그다음 사용자 순서")
    void boxForDate() throws Exception {
        long day = create("하루", "DAY", "2026-09-26", "2026-09-26");
        create("기간", "PERIOD", "2026-09-24", "2026-09-30");
        create("주간", "WEEK", "2026-09-20", "2026-09-26");
        create("월간", "MONTH", "2026-09-01", "2026-09-30");
        create("다른 날", "DAY", "2026-09-27", "2026-09-27");
        patchTodo(day, "{\"completed\":true}").andExpect(status().isOk());

        mvc.perform(get("/api/v1/todos").param("date", "2026-09-26"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].title").value(contains("기간", "주간", "월간", "하루")))
            .andExpect(jsonPath("$[3].completed").value(true));
    }

    @Test
    @DisplayName("지난 미완료: 원래 날짜 박스와 오늘 박스 모두, 빨간 ! (overdue), 다른 날 박스에는 없음 (D-029)")
    void overdueInTodayBox() throws Exception {
        create("오늘 할 일", "DAY", "2026-09-25", "2026-09-25");
        create("지난 미완료", "DAY", "2026-09-23", "2026-09-23");
        long done = create("지난 완료", "DAY", "2026-09-22", "2026-09-22");
        patchTodo(done, "{\"completed\":true}");

        mvc.perform(get("/api/v1/todos").param("date", "2026-09-25"))
            .andExpect(jsonPath("$[*].title").value(contains("오늘 할 일", "지난 미완료")))
            .andExpect(jsonPath("$[0].overdue").value(false))
            .andExpect(jsonPath("$[1].overdue").value(true));
        mvc.perform(get("/api/v1/todos").param("date", "2026-09-23"))
            .andExpect(jsonPath("$[*].title").value(contains("지난 미완료")))
            .andExpect(jsonPath("$[0].overdue").value(true));
        mvc.perform(get("/api/v1/todos").param("date", "2026-09-24"))
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("날짜를 바꾸면 경고가 풀린다 (TODO-13)")
    void changeDateClearsOverdue() throws Exception {
        long id = create("지난 미완료", "DAY", "2026-09-23", "2026-09-23");
        patchTodo(id, "{\"startDate\":\"2026-09-26\",\"endDate\":\"2026-09-26\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overdue").value(false));
        mvc.perform(get("/api/v1/todos").param("date", "2026-09-25")).andExpect(jsonPath("$", hasSize(0)));
    }

    // ------------------------------------------------------------------ 완료 (US-13)

    @Test
    @DisplayName("완료: completedAt = 지금, 다시 true여도 시각 유지, 취소하면 null. 기간 Todo도 한 번이면 전체 완료")
    void complete() throws Exception {
        long id = create("분기 보고서", "PERIOD", "2026-09-24", "2026-09-30");
        patchTodo(id, "{\"completed\":true}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.completedAt").value("2026-09-25T09:00:00"));
        mvc.perform(get("/api/v1/todos").param("date", "2026-09-28"))
            .andExpect(jsonPath("$[0].completed").value(true));
        patchTodo(id, "{\"completed\":true,\"title\":\"분기 보고서 v2\"}")
            .andExpect(jsonPath("$.completedAt").value("2026-09-25T09:00:00"));
        patchTodo(id, "{\"completed\":false}")
            .andExpect(jsonPath("$.completed").value(false))
            .andExpect(jsonPath("$.completedAt").value(nullValue()));
        patchTodo(id, "{\"completed\":null}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("completed"));
    }

    @Test
    @DisplayName("그날 완료한 Todo (completedOn, DIARY-04)")
    void completedOn() throws Exception {
        long a = create("A", "DAY", "2026-09-25", "2026-09-25");
        create("B", "DAY", "2026-09-25", "2026-09-25");
        long c = create("C", "PERIOD", "2026-09-20", "2026-09-30");
        patchTodo(a, "{\"completed\":true}");
        patchTodo(c, "{\"completed\":true}");
        jdbc.update("INSERT INTO todos (category_id, title, start_date, end_date, completed, completed_at) VALUES (?, '어제 완료', '2026-09-24', '2026-09-24', 'Y', '2026-09-24 23:59:59')", defaultCategoryId());

        mvc.perform(get("/api/v1/todos").param("completedOn", "2026-09-25"))
            .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.containsInAnyOrder("A", "C")));
        mvc.perform(get("/api/v1/todos").param("completedOn", "2026-09-24"))
            .andExpect(jsonPath("$[*].title").value(contains("어제 완료")));
    }

    // ------------------------------------------------------------------ 시간표 (US-15)

    @Test
    @DisplayName("시간표: 기간과 겹치고 시간이 있는 Todo만, 날짜·시각 순. 기간 Todo도 포함 (D-027)")
    void scheduled() throws Exception {
        postTodo("{\"title\":\"저녁 공부\",\"type\":\"MONTH\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\",\"time\":{\"start\":\"21:00\",\"durationMinutes\":60}}");
        postTodo("{\"title\":\"오전 회의 준비\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"time\":{\"start\":\"09:30\",\"durationMinutes\":30}}");
        create("시간 없음", "DAY", "2026-09-25", "2026-09-25");
        postTodo("{\"title\":\"범위 밖\",\"type\":\"DAY\",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-05\",\"time\":{\"start\":\"10:00\",\"durationMinutes\":30}}");

        mvc.perform(get("/api/v1/todos").param("from", "2026-09-20").param("to", "2026-09-26").param("scheduled", "true"))
            .andExpect(jsonPath("$[*].title").value(contains("저녁 공부", "오전 회의 준비")));
        mvc.perform(get("/api/v1/todos").param("from", "2026-09-20").param("to", "2026-09-26"))
            .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("시간 지우기: time에 null → 시간표에서 빠짐")
    void clearTime() throws Exception {
        String body = postTodo("{\"title\":\"x\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"time\":{\"start\":\"10:00\",\"durationMinutes\":30}}")
            .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
        patchTodo(id, "{\"time\":{\"durationMinutes\":45}}")
            .andExpect(jsonPath("$.time.start").value("10:00"))
            .andExpect(jsonPath("$.time.durationMinutes").value(45));
        patchTodo(id, "{\"time\":null}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.time").value(nullValue()));
    }

    // ------------------------------------------------------------------ 조회 조건

    @Test
    @DisplayName("조회 조건: 없음·둘 이상·from만·scheduled 단독·62일 초과 → 400 INVALID_QUERY. 카테고리 필터")
    void queryConditions() throws Exception {
        mvc.perform(get("/api/v1/todos")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mvc.perform(get("/api/v1/todos").param("date", "2026-09-25").param("completedOn", "2026-09-25"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mvc.perform(get("/api/v1/todos").param("from", "2026-09-25"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mvc.perform(get("/api/v1/todos").param("date", "2026-09-25").param("scheduled", "true"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mvc.perform(get("/api/v1/todos").param("from", "2026-09-01").param("to", "2026-11-02"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));

        jdbc.update("INSERT INTO categories (name, color, sort_order) VALUES ('공부', '#2F62A8', 1)");
        long study = jdbc.queryForObject("SELECT category_id FROM categories WHERE name = '공부'", Long.class);
        postTodo("{\"title\":\"공부 Todo\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"categoryId\":" + study + "}");
        create("미지정 Todo", "DAY", "2026-09-25", "2026-09-25");
        mvc.perform(get("/api/v1/todos").param("date", "2026-09-25").param("categoryId", String.valueOf(study)))
            .andExpect(jsonPath("$[*].title").value(contains("공부 Todo")));
    }

    // ------------------------------------------------------------------ 순서 (US-14)·삭제

    @Test
    @DisplayName("순서 이동: afterId 뒤로, null이면 맨 앞, 0부터 다시 매김. 새 Todo는 맨 뒤")
    void move() throws Exception {
        long a = create("A", "DAY", "2026-09-25", "2026-09-25");
        long b = create("B", "DAY", "2026-09-25", "2026-09-25");
        long c = create("C", "DAY", "2026-09-25", "2026-09-25");
        mvc.perform(put("/api/v1/todos/" + a + "/position").contentType(MediaType.APPLICATION_JSON).content("{\"afterId\":" + c + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sortOrder").value(2));
        mvc.perform(put("/api/v1/todos/" + c + "/position").contentType(MediaType.APPLICATION_JSON).content("{\"afterId\":null}"))
            .andExpect(status().isOk());
        create("D", "DAY", "2026-09-25", "2026-09-25");
        mvc.perform(get("/api/v1/todos").param("date", "2026-09-25"))
            .andExpect(jsonPath("$[*].title").value(contains("C", "B", "A", "D")))
            .andExpect(jsonPath("$[*].sortOrder").value(contains(0, 1, 2, 3)));
        mvc.perform(put("/api/v1/todos/" + b + "/position").contentType(MediaType.APPLICATION_JSON).content("{\"afterId\":9999}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("afterId"));
    }

    @Test
    @DisplayName("삭제 → 204, 이후 404, 박스에서 사라짐")
    void deleteTodo() throws Exception {
        long id = create("A", "DAY", "2026-09-25", "2026-09-25");
        mvc.perform(delete("/api/v1/todos/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/todos/" + id)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/todos").param("date", "2026-09-25")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/v1/todos/" + id)).andExpect(status().isNotFound());
    }
}
