package com.awesomedesk.j_planner.api.v1.dday;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** D-Day API 세부 규칙 (08-api-design.md 6절). 오늘 = 2026-09-25 */
class DdayApiTest extends IntegrationTest {

    private ResultActions postDday(String json) throws Exception {
        return mvc.perform(post("/api/v1/ddays").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void expectFieldError(String json, String field) throws Exception {
        postDday(json)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value(field));
    }

    @Test
    @DisplayName("추가 → 201 + Location, 제목 앞뒤 공백 제거, 맨 뒤, 오늘 값·표시 글자")
    void create() throws Exception {
        postDday("{\"title\":\" 시험 \",\"targetDate\":\"2026-09-28\"}");
        postDday("{\"title\":\" 가족 여행 \",\"targetDate\":\"2026-09-25\"}")
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("/api/v1/ddays/\\d+")))
            .andExpect(jsonPath("$.title").value("가족 여행"))
            .andExpect(jsonPath("$.sortOrder").value(1))
            .andExpect(jsonPath("$.today.value").value(0))
            .andExpect(jsonPath("$.today.label").value("D-Day"));
    }

    @Test
    @DisplayName("입력값 오류 → 400 + 필드")
    void invalid() throws Exception {
        expectFieldError("{\"title\":\" \",\"targetDate\":\"2026-09-28\"}", "title");
        expectFieldError("{\"title\":\"x\"}", "targetDate");
        expectFieldError("{\"title\":\"x\",\"targetDate\":\"2026-09-28\",\"display\":{\"interval\":{\"enabled\":true,\"days\":0}}}",
            "display.interval.days");
        expectFieldError("{\"title\":\"x\",\"targetDate\":\"2026-09-28\",\"display\":{\"lastDays\":{\"enabled\":true,\"days\":0}}}",
            "display.lastDays.days");
        postDday("{\"title\":\"x\",\"targetDate\":\"2026-09-28\",\"countType\":\"DAYS\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PATCH: 기준을 바꿔 옵션이 맞지 않게 되면 400, 옵션도 같이 끄면 200")
    void patchCountType() throws Exception {
        String body = postDday("{\"title\":\"x\",\"targetDate\":\"2026-07-01\",\"countType\":\"COUNTUP\"}")
            .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));

        mvc.perform(patch("/api/v1/ddays/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"countType\":\"COUNTDOWN\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("display.yearly"));
        mvc.perform(patch("/api/v1/ddays/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"countType\":\"COUNTDOWN\",\"display\":{\"yearly\":false}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.countType").value("COUNTDOWN"))
            .andExpect(jsonPath("$.display.interval.enabled").value(true));
    }

    @Test
    @DisplayName("PATCH: 제목·날짜 null → 400, 없는 D-Day → 404, 순서 이동 오류 → 400")
    void errors() throws Exception {
        String body = postDday("{\"title\":\"x\",\"targetDate\":\"2026-09-28\"}").andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));

        mvc.perform(patch("/api/v1/ddays/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"targetDate\":null}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/ddays/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"display\":null}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/ddays/999999")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(put("/api/v1/ddays/" + id + "/position").contentType(MediaType.APPLICATION_JSON).content("{\"afterId\":" + id + "}"))
            .andExpect(status().isBadRequest());
    }
}
