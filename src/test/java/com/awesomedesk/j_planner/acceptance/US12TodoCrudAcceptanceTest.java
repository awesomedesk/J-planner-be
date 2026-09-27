package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-12 사용자는 하루·기간·주간·월간 Todo를 추가·수정·삭제한다 (09-backlog.md)
 * <ul>
 *   <li>AC2 Theme2 표시·즐겨찾기/완성도 점선 자리 → FE 담당. 색 null 저장만 여기서</li>
 *   <li>AC3 모바일 수정 화면 아래 'Todo 삭제' 버튼 위치 → FE 담당. 삭제 API만 여기서</li>
 * </ul>
 */
@DisplayName("US-12 Todo 추가·수정·삭제")
class US12TodoCrudAcceptanceTest extends AcceptanceTest {

    private static final String URL = "/api/v1/todos";

    @Test
    @DisplayName("AC1: 종류는 하루·기간·주간·월간 네 가지")
    void fourTypes() throws Exception {
        createTodo("하루", "DAY", "2026-09-25", "2026-09-25");
        createTodo("기간", "PERIOD", "2026-09-24", "2026-09-30");
        createTodo("주간", "WEEK", "2026-09-20", "2026-09-26");
        createTodo("월간", "MONTH", "2026-09-01", "2026-09-30");

        getJson(URL + "?date=" + TODAY)
            .andExpect(jsonPath("$[*].type", contains("DAY", "PERIOD", "WEEK", "MONTH")));
        postJson(URL, "{\"title\":\"x\",\"type\":\"YEAR\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-12-31\"}")
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC1: 기간 Todo는 기간 동안 매일 박스에 보인다 (기간 밖에는 없다)")
    void periodTodoEveryDay() throws Exception {
        long period = createTodo("책 읽기", "PERIOD", "2026-09-25", "2026-09-28");
        int id = (int) period;

        for (String date : new String[] {"2026-09-25", "2026-09-26", "2026-09-27", "2026-09-28"}) {
            getJson(URL + "?date=" + date).andExpect(jsonPath("$[*].id", hasItem(id)));
        }
        getJson(URL + "?date=2026-09-29").andExpect(jsonPath("$[*].id", not(hasItem(id))));
    }

    @Test
    @DisplayName("AC2: 카테고리는 기본 미지정, 고를 수도 있다. 색을 안 고르면 null (FE가 Theme2)")
    void categoryAndColor() throws Exception {
        long plain = createDayTodo("장보기", TODAY);
        getJson(URL + "/" + plain)
            .andExpect(jsonPath("$.categoryId").value(defaultCategoryId()))
            .andExpect(jsonPath("$.color").value(nullValue()));

        long study = createCategory("공부");
        postJson(URL, "{\"title\":\"복습\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"categoryId\":"
            + study + ",\"color\":\"#2F62A8\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.categoryId").value(study))
            .andExpect(jsonPath("$.color").value("#2F62A8"));
    }

    @Test
    @DisplayName("AC3: 새 Todo는 박스 맨 아래 (D-029). 순서를 바꾼 뒤에 추가해도 맨 아래")
    void newTodoAtBottom() throws Exception {
        createDayTodo("A", TODAY);
        long b = createDayTodo("B", TODAY);
        putJson(URL + "/" + b + "/position", "{\"afterId\":null}").andExpect(status().isOk());
        createDayTodo("C", TODAY);

        getJson(URL + "?date=" + TODAY).andExpect(jsonPath("$[*].title", contains("B", "A", "C")));
    }

    @Test
    @DisplayName("수정: 보낸 값만 바뀐다 (제목·종류·날짜·카테고리·색)")
    void update() throws Exception {
        long id = createDayTodo("장보기", TODAY);
        patchJson(URL + "/" + id, "{\"title\":\"주간 장보기\",\"type\":\"WEEK\",\"startDate\":\"2026-09-20\",\"endDate\":\"2026-09-26\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("주간 장보기"))
            .andExpect(jsonPath("$.type").value("WEEK"))
            .andExpect(jsonPath("$.startDate").value("2026-09-20"))
            .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    @DisplayName("AC3: 삭제하면 박스에서 사라지고 다시 볼 수 없다 (D-030)")
    void delete() throws Exception {
        long id = createDayTodo("장보기", TODAY);
        deleteJson(URL + "/" + id).andExpect(status().isNoContent());

        getJson(URL + "/" + id).andExpect(status().isNotFound());
        getJson(URL + "?date=" + TODAY).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("D-041: 새로 만들 때 completed는 무시하고 항상 미완료")
    void createIgnoresCompleted() throws Exception {
        postJson(URL, "{\"title\":\"x\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"completed\":true}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.completed").value(false))
            .andExpect(jsonPath("$.completedAt").value(nullValue()));
    }

    @Test
    @DisplayName("AC4: 주간 Todo 시작일은 저장 시점의 주 시작 요일 기준. 설정을 바꿔도 기존 Todo는 그대로 (D-041)")
    void weekStartAtSaveTime() throws Exception {
        long sundayWeek = createTodo("일요일 주간", "WEEK", "2026-09-20", "2026-09-26");

        patchJson("/api/v1/settings", "{\"weekStartDay\":\"MON\"}").andExpect(status().isOk());

        // 기존 Todo는 그대로 보인다
        getJson(URL + "/" + sundayWeek)
            .andExpect(jsonPath("$.startDate").value("2026-09-20"))
            .andExpect(jsonPath("$.endDate").value("2026-09-26"));
        getJson(URL + "?date=" + TODAY).andExpect(jsonPath("$[*].id", hasItem((int) sundayWeek)));
        // 새 Todo는 바뀐 설정(월요일)을 따른다
        postJson(URL, "{\"title\":\"x\",\"type\":\"WEEK\",\"startDate\":\"2026-09-20\",\"endDate\":\"2026-09-26\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("startDate"));
        createTodo("월요일 주간", "WEEK", "2026-09-21", "2026-09-27");
    }

    @Test
    @DisplayName("AC4: 주 시작 요일을 바꾼 뒤에도 예전 주간 Todo의 제목·색·시간·완료는 고칠 수 있고, 날짜를 바꿀 때만 새 기준 (D-042)")
    void oldWeekTodoEditable() throws Exception {
        long sundayWeek = createTodo("일요일 주간", "WEEK", "2026-09-20", "2026-09-26");
        patchJson("/api/v1/settings", "{\"weekStartDay\":\"MON\"}").andExpect(status().isOk());

        patchJson(URL + "/" + sundayWeek, "{\"title\":\"운동 3회\",\"color\":\"#2F62A8\",\"time\":{\"start\":\"07:00\",\"durationMinutes\":40}}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("운동 3회"))
            .andExpect(jsonPath("$.startDate").value("2026-09-20"));
        patchJson(URL + "/" + sundayWeek, "{\"completed\":true}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(true));

        // 날짜를 바꾸면 새 기준(월요일)을 따른다. 자동으로 옮기지 않는다
        patchJson(URL + "/" + sundayWeek, "{\"startDate\":\"2026-09-27\",\"endDate\":\"2026-10-03\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("startDate"));
        patchJson(URL + "/" + sundayWeek, "{\"startDate\":\"2026-09-28\",\"endDate\":\"2026-10-04\"}")
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC4: 다른 종류를 주간으로 바꿀 때도 새 기준으로 검사한다 (D-042)")
    void changeTypeToWeekChecked() throws Exception {
        long day = createDayTodo("장보기", TODAY);
        patchJson("/api/v1/settings", "{\"weekStartDay\":\"MON\"}").andExpect(status().isOk());

        patchJson(URL + "/" + day, "{\"type\":\"WEEK\",\"startDate\":\"2026-09-20\",\"endDate\":\"2026-09-26\"}")
            .andExpect(status().isBadRequest());
        patchJson(URL + "/" + day, "{\"type\":\"WEEK\",\"startDate\":\"2026-09-21\",\"endDate\":\"2026-09-27\"}")
            .andExpect(status().isOk());
    }
}
