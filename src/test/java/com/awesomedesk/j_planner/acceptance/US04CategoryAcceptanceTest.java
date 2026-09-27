package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-04 사용자는 카테고리를 만들고·고치고·지우고·순서를 바꾼다 (09-backlog.md)
 * <ul>
 *   <li>AC6 여는 곳(필터 드롭다운·설정·모바일 메뉴) → FE 담당</li>
 *   <li>AC7 중 "그 줄에서 바로 수정·확인/취소·6색 선택" → FE 담당. 색 null·미지정 색 잠금만 여기서</li>
 *   <li>AC8 창 바깥 닫기·모바일 전체 화면 → FE 담당</li>
 * </ul>
 */
@DisplayName("US-04 카테고리 관리")
class US04CategoryAcceptanceTest extends AcceptanceTest {

    private static final String URL = "/api/v1/categories";

    @Test
    @DisplayName("AC1: 기본 카테고리 미지정(회색)은 항상 있고, 삭제·이름 변경 불가, 항상 맨 위 (D-014, D-029)")
    void defaultCategory() throws Exception {
        long other = createCategory("공부");
        long def = defaultCategoryId();

        getJson(URL)
            .andExpect(jsonPath("$[0].name").value("미지정"))
            .andExpect(jsonPath("$[0].isDefault").value(true))
            .andExpect(jsonPath("$[0].color").value("#6B6B6B"));

        deleteJson(URL + "/" + def).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DEFAULT_CATEGORY_LOCKED"));
        patchJson(URL + "/" + def, "{\"name\":\"기타\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DEFAULT_CATEGORY_LOCKED"));
        // 다른 카테고리를 맨 앞으로 옮겨도 미지정이 맨 위
        putJson(URL + "/" + other + "/position", "{\"afterId\":null}").andExpect(status().isOk());
        getJson(URL).andExpect(jsonPath("$[0].name").value("미지정"));
    }

    @Test
    @DisplayName("AC2: 같은 이름은 추가·이름 변경 불가, 안내 문구가 뜬다. 새 카테고리는 맨 뒤 (D-029, D-032)")
    void uniqueNameAndAppend() throws Exception {
        createCategory("공부");
        long work = createCategory("업무");

        getJson(URL).andExpect(jsonPath("$[*].name", contains("미지정", "공부", "업무")));

        postJson(URL, "{\"name\":\"공부\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CATEGORY_NAME_DUPLICATED"))
            .andExpect(jsonPath("$.detail").isString());
        patchJson(URL + "/" + work, "{\"name\":\"공부\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CATEGORY_NAME_DUPLICATED"));
        postJson(URL, "{\"name\":\"미지정\"}").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("AC3: ▲▼로 순서 변경(미지정 제외). 이 순서가 목록 순서")
    void reorder() throws Exception {
        long a = createCategory("A");
        long b = createCategory("B");
        long c = createCategory("C");

        // C를 A 뒤로 (▲)
        putJson(URL + "/" + c + "/position", "{\"afterId\":" + a + "}").andExpect(status().isOk());
        getJson(URL).andExpect(jsonPath("$[*].name", contains("미지정", "A", "C", "B")));
        // A를 B 뒤로 (▼▼)
        putJson(URL + "/" + a + "/position", "{\"afterId\":" + b + "}").andExpect(status().isOk());
        getJson(URL).andExpect(jsonPath("$[*].name", contains("미지정", "C", "B", "A")));
        // 미지정은 옮길 수 없다
        putJson(URL + "/" + defaultCategoryId() + "/position", "{\"afterId\":" + a + "}")
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("AC4: 각 카테고리에 연결된 일정·Todo 수. 삭제된 항목 제외, 완료한 Todo 포함 (D-032)")
    void linkedCounts() throws Exception {
        long study = createCategory("공부");
        createSchedule("수업", "2026-09-25T10:00:00", "2026-09-25T11:00:00", study);
        long deletedSchedule = createSchedule("취소된 수업", "2026-09-26T10:00:00", "2026-09-26T11:00:00", study);
        deleteJson("/api/v1/schedules/" + deletedSchedule).andExpect(status().isNoContent());

        long done = createTodo("{\"title\":\"복습\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"categoryId\":" + study + "}");
        patchJson("/api/v1/todos/" + done, "{\"completed\":true}").andExpect(status().isOk());
        createTodo("{\"title\":\"예습\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"categoryId\":" + study + "}");
        long deletedTodo = createTodo("{\"title\":\"취소\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"categoryId\":" + study + "}");
        deleteJson("/api/v1/todos/" + deletedTodo).andExpect(status().isNoContent());

        getJson(URL)
            .andExpect(jsonPath("$[1].name").value("공부"))
            .andExpect(jsonPath("$[1].scheduleCount").value(1))
            .andExpect(jsonPath("$[1].todoCount").value(2));
    }

    @Test
    @DisplayName("AC5: 삭제하면 연결된 일정·Todo는 미지정으로 옮겨진다")
    void deleteMovesItemsToDefault() throws Exception {
        long study = createCategory("공부");
        long schedule = createSchedule("수업", "2026-09-25T10:00:00", "2026-09-25T11:00:00", study);
        long todo = createTodo("{\"title\":\"복습\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"categoryId\":" + study + "}");

        deleteJson(URL + "/" + study).andExpect(status().isNoContent());

        getJson(URL).andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].scheduleCount").value(1))
            .andExpect(jsonPath("$[0].todoCount").value(1));
        getJson("/api/v1/schedules/" + schedule).andExpect(jsonPath("$.categoryId").value(defaultCategoryId()));
        getJson("/api/v1/todos/" + todo).andExpect(jsonPath("$.categoryId").value(defaultCategoryId()));
    }

    @Test
    @DisplayName("AC7: 색은 처음엔 선택 안 됨 → 안 고르면 null. 미지정 색 변경 불가 (D-037)")
    void colorOptionalAndDefaultLocked() throws Exception {
        postJson(URL, "{\"name\":\"운동\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.color").value(nullValue()));
        long colored = createCategory("공부");
        patchJson(URL + "/" + colored, "{\"color\":\"#2F62A8\"}").andExpect(jsonPath("$.color").value("#2F62A8"));
        patchJson(URL + "/" + colored, "{\"color\":null}").andExpect(jsonPath("$.color").value(nullValue()));

        patchJson(URL + "/" + defaultCategoryId(), "{\"color\":\"#2F62A8\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DEFAULT_CATEGORY_LOCKED"));
    }
}
