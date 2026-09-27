package com.awesomedesk.j_planner.api.v1.diary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 일기 API 세부 규칙 (08-api-design.md 7절) */
class DiaryApiTest extends IntegrationTest {

    @Test
    @DisplayName("빈 내용(없음·빈 문자열·공백만) → 400 content. 지우려면 DELETE")
    void emptyContent() throws Exception {
        for (String body : new String[] {"{}", "{\"content\":\"\"}", "{\"content\":\"  \\n \"}"}) {
            mvc.perform(put("/api/v1/diaries/2026-09-25").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("content"));
        }
    }

    @Test
    @DisplayName("내용은 보낸 그대로 (줄바꿈·앞뒤 공백 유지)")
    void contentAsIs() throws Exception {
        mvc.perform(put("/api/v1/diaries/2026-09-25").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  첫 줄\\n둘째 줄 \"}"))
            .andExpect(jsonPath("$.content").value("  첫 줄\n둘째 줄 "));
    }

    @Test
    @DisplayName("날짜 형식 오류 → 400, 기간 조회 조건 오류(없음·거꾸로·62일 초과) → 400 INVALID_QUERY")
    void invalidDateAndQuery() throws Exception {
        mvc.perform(get("/api/v1/diaries/2026-13-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/diaries")).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mvc.perform(get("/api/v1/diaries?from=2026-09-10&to=2026-09-01")).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mvc.perform(get("/api/v1/diaries?from=2026-09-01&to=2026-11-02")).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        mvc.perform(get("/api/v1/diaries?from=2026-09-01&to=2026-11-01")).andExpect(status().isOk());
    }
}
