package com.awesomedesk.j_planner.acceptance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.awesomedesk.j_planner.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 인수 테스트(Acceptance Test)의 부모.
 * <p>
 * 09-backlog.md의 인수 조건(AC) 한 줄 = 테스트 하나. 클래스는 스토리 하나(US-xx),
 * 테스트 이름은 {@code "ACn: <인수 조건 문장>"}으로 붙여 기획과 1:1로 맞춘다.
 * FE만 확인할 수 있는 인수 조건(화면 배치·끌기 등)은 클래스 설명에 "FE 담당"으로 적고 테스트하지 않는다.
 * <p>
 * 오늘 = 2026-09-25(금), 주 시작 = 일요일 (FixedClockConfig, reset.sql)
 */
abstract class AcceptanceTest extends IntegrationTest {

    protected static final String TODAY = "2026-09-25";

    // ------------------------------------------------------------------ HTTP

    protected ResultActions getJson(String url) throws Exception {
        return mvc.perform(get(url));
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions patchJson(String url, String json) throws Exception {
        return mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions putJson(String url, String json) throws Exception {
        return mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions deleteJson(String url) throws Exception {
        return mvc.perform(delete(url));
    }

    /** 응답 본문에서 JsonPath 값을 꺼낸다 */
    protected static <T> T read(ResultActions result, String path) throws Exception {
        MockHttpServletResponse response = result.andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), path);
    }

    protected static long idOf(ResultActions created) throws Exception {
        return ((Number) read(created, "$.id")).longValue();
    }

    protected static List<Integer> ids(ResultActions result) throws Exception {
        return read(result, "$[*].id");
    }

    // ------------------------------------------------------------------ 준비 데이터

    protected long createCategory(String name) throws Exception {
        return idOf(postJson("/api/v1/categories", "{\"name\":\"" + name + "\"}"));
    }

    protected long createSchedule(String title, String start, String end, Long categoryId) throws Exception {
        String category = categoryId == null ? "" : ",\"categoryId\":" + categoryId;
        return idOf(postJson("/api/v1/schedules",
            "{\"title\":\"" + title + "\",\"allDay\":false,\"start\":\"" + start + "\",\"end\":\"" + end + "\"" + category + "}"));
    }

    protected long createTodo(String title, String type, String start, String end) throws Exception {
        return createTodo("{\"title\":\"" + title + "\",\"type\":\"" + type + "\",\"startDate\":\"" + start
            + "\",\"endDate\":\"" + end + "\"}");
    }

    protected long createTodo(String json) throws Exception {
        return idOf(postJson("/api/v1/todos", json));
    }

    protected long createDayTodo(String title, String date) throws Exception {
        return createTodo(title, "DAY", date, date);
    }
}
