package com.awesomedesk.j_planner.api.v1.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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

/** US-04 카테고리 API (08-api-design.md 3절) */
class CategoryApiTest extends IntegrationTest {

    private long create(String name, String color) throws Exception {
        String body = mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"color\":\"" + color + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    @DisplayName("처음에는 미지정만 있다")
    void listOnlyDefault() throws Exception {
        mvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("미지정"))
            .andExpect(jsonPath("$[0].isDefault").value(true))
            .andExpect(jsonPath("$[0].scheduleCount").value(0))
            .andExpect(jsonPath("$[0].todoCount").value(0));
    }

    @Test
    @DisplayName("추가 → 201 + Location, 이름 앞뒤 공백 제거, 맨 뒤에 들어감")
    void createAppendsToEnd() throws Exception {
        create("공부", "#2F62A8");
        mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  업무  \",\"color\":\"#A6323F\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/categories/\\d+")))
            .andExpect(jsonPath("$.name").value("업무"))
            .andExpect(jsonPath("$.isDefault").value(false))
            .andExpect(jsonPath("$.sortOrder").value(2));

        mvc.perform(get("/api/v1/categories"))
            .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains("미지정", "공부", "업무")));
    }

    @Test
    @DisplayName("같은 이름 추가 → 409 CATEGORY_NAME_DUPLICATED")
    void createDuplicate() throws Exception {
        create("공부", "#2F62A8");
        mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"공부\",\"color\":\"#000000\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CATEGORY_NAME_DUPLICATED"));
        mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"미지정\",\"color\":\"#000000\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CATEGORY_NAME_DUPLICATED"));
    }

    @Test
    @DisplayName("입력값 오류 → 400 VALIDATION_FAILED + 필드별 errors")
    void createInvalid() throws Exception {
        mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \",\"color\":\"blue\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder("name", "color")));
    }

    @Test
    @DisplayName("PATCH: 보낸 필드만 바뀐다")
    void patchOnlySentFields() throws Exception {
        long id = create("공부", "#2F62A8");
        mvc.perform(patch("/api/v1/categories/" + id).contentType("application/merge-patch+json")
                .content("{\"color\":\"#2F7A4B\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("공부"))
            .andExpect(jsonPath("$.color").value("#2F7A4B"));
    }

    @Test
    @DisplayName("PATCH: 다른 카테고리와 같은 이름 → 409, 자기 이름 그대로는 OK")
    void patchDuplicateName() throws Exception {
        create("공부", "#2F62A8");
        long id = create("업무", "#A6323F");
        mvc.perform(patch("/api/v1/categories/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"공부\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CATEGORY_NAME_DUPLICATED"));
        mvc.perform(patch("/api/v1/categories/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"업무\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH: name에 null → 400 (필수값)")
    void patchNullName() throws Exception {
        long id = create("공부", "#2F62A8");
        mvc.perform(patch("/api/v1/categories/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":null}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("미지정: 색은 바꿀 수 있고, 이름 변경·삭제·이동은 409 DEFAULT_CATEGORY_LOCKED")
    void defaultCategoryLocked() throws Exception {
        long defaultId = defaultCategoryId();
        mvc.perform(patch("/api/v1/categories/" + defaultId).contentType(MediaType.APPLICATION_JSON).content("{\"color\":\"#555555\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.color").value("#555555"));
        mvc.perform(patch("/api/v1/categories/" + defaultId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"기타\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DEFAULT_CATEGORY_LOCKED"));
        mvc.perform(delete("/api/v1/categories/" + defaultId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DEFAULT_CATEGORY_LOCKED"));
        mvc.perform(put("/api/v1/categories/" + defaultId + "/position").contentType(MediaType.APPLICATION_JSON).content("{\"afterId\":null}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DEFAULT_CATEGORY_LOCKED"));
    }

    @Test
    @DisplayName("연결 개수: 삭제된 항목 제외, 완료한 Todo 포함 (D-032)")
    void counts() throws Exception {
        long id = create("공부", "#2F62A8");
        jdbc.update("INSERT INTO calendars (category_id, title, start_date_time, end_date_time) VALUES (?, '수업', '2026-09-24 10:00:00', '2026-09-24 11:00:00')", id);
        jdbc.update("INSERT INTO calendars (category_id, title, start_date_time, end_date_time, deleted) VALUES (?, '지운 일정', '2026-09-24 10:00:00', '2026-09-24 11:00:00', 'Y')", id);
        jdbc.update("INSERT INTO todos (category_id, title, start_date, end_date) VALUES (?, '미완료', '2026-09-24', '2026-09-24')", id);
        jdbc.update("INSERT INTO todos (category_id, title, start_date, end_date, completed, completed_at) VALUES (?, '완료', '2026-09-24', '2026-09-24', 'Y', NOW())", id);
        jdbc.update("INSERT INTO todos (category_id, title, start_date, end_date, deleted) VALUES (?, '지운 Todo', '2026-09-24', '2026-09-24', 'Y')", id);

        mvc.perform(get("/api/v1/categories"))
            .andExpect(jsonPath("$[1].name").value("공부"))
            .andExpect(jsonPath("$[1].scheduleCount").value(1))
            .andExpect(jsonPath("$[1].todoCount").value(2));
    }

    @Test
    @DisplayName("삭제 → 204, 연결된 일정·Todo(완료 포함)는 미지정으로, 목록에서 사라짐")
    void deleteMovesItemsToDefault() throws Exception {
        long id = create("공부", "#2F62A8");
        long defaultId = defaultCategoryId();
        jdbc.update("INSERT INTO calendars (category_id, title, start_date_time, end_date_time) VALUES (?, '수업', '2026-09-24 10:00:00', '2026-09-24 11:00:00')", id);
        jdbc.update("INSERT INTO todos (category_id, title, start_date, end_date, completed, completed_at) VALUES (?, '완료', '2026-09-24', '2026-09-24', 'Y', NOW())", id);

        mvc.perform(delete("/api/v1/categories/" + id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/categories"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].scheduleCount").value(1))
            .andExpect(jsonPath("$[0].todoCount").value(1));
        assertThat(jdbc.queryForObject("SELECT deleted FROM categories WHERE category_id = ?", String.class, id)).isEqualTo("Y");
        assertThat(jdbc.queryForObject("SELECT category_id FROM calendars", Long.class)).isEqualTo(defaultId);

        mvc.perform(delete("/api/v1/categories/" + id)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("삭제한 카테고리 이름은 다시 쓸 수 있다")
    void reuseDeletedName() throws Exception {
        long id = create("공부", "#2F62A8");
        mvc.perform(delete("/api/v1/categories/" + id)).andExpect(status().isNoContent());
        create("공부", "#2F62A8");
    }

    @Test
    @DisplayName("순서 이동: afterId 뒤로, null이면 미지정 바로 뒤, 미지정 id도 맨 앞")
    void move() throws Exception {
        long a = create("A", "#111111");
        long b = create("B", "#222222");
        long c = create("C", "#333333");

        mvc.perform(put("/api/v1/categories/" + a + "/position").contentType(MediaType.APPLICATION_JSON).content("{\"afterId\":" + c + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sortOrder").value(3));
        mvc.perform(get("/api/v1/categories"))
            .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains("미지정", "B", "C", "A")));

        mvc.perform(put("/api/v1/categories/" + c + "/position").contentType(MediaType.APPLICATION_JSON).content("{\"afterId\":null}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/v1/categories/" + a + "/position").contentType(MediaType.APPLICATION_JSON).content("{\"afterId\":" + defaultCategoryId() + "}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/categories"))
            .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains("미지정", "A", "C", "B")))
            .andExpect(jsonPath("$[*].sortOrder").value(org.hamcrest.Matchers.contains(0, 1, 2, 3)));
    }

    @Test
    @DisplayName("순서 이동: 없는 afterId·자기 자신 → 400, 없는 카테고리 → 404")
    void moveInvalid() throws Exception {
        long a = create("A", "#111111");
        mvc.perform(put("/api/v1/categories/" + a + "/position").contentType(MediaType.APPLICATION_JSON).content("{\"afterId\":9999}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("afterId"));
        mvc.perform(put("/api/v1/categories/" + a + "/position").contentType(MediaType.APPLICATION_JSON).content("{\"afterId\":" + a + "}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/categories/9999/position").contentType(MediaType.APPLICATION_JSON).content("{\"afterId\":null}"))
            .andExpect(status().isNotFound());
    }
}
