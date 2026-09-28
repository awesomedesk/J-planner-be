package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * US-03 (기술) FE가 BE API를 호출하고 오류를 일관되게 보여준다 — D-031
 * <ul>
 *   <li>AC1 서버·네트워크 오류 시 화면이 깨지지 않고 짧은 안내 → FE 담당</li>
 *   <li>AC2 오류 형식은 Problem Details, AwesomeResponse 흔적 없음 → 여기서 확인</li>
 * </ul>
 */
@DisplayName("US-03 API 호출과 오류 형식")
class US03ApiErrorAcceptanceTest extends AcceptanceTest {

    @Test
    @DisplayName("AC2: 성공 응답은 감싸지 않은 리소스 그대로 (AwesomeResponse 흔적 없음)")
    void successIsNotWrapped() throws Exception {
        getJson("/api/v1/health")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.success").doesNotExist());
        getJson("/api/v1/categories")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].name").value("미지정"));
    }

    @Test
    @DisplayName("AC2: 오류는 Problem Details(application/problem+json) + code·errors, 화면에 보여줄 한국어 detail")
    void errorsAreProblemDetails() throws Exception {
        // 404
        getJson("/api/v1/schedules/999999")
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.detail").value("일정을 찾을 수 없습니다: 999999"))
            .andExpect(jsonPath("$.instance").value("/api/v1/schedules/999999"))
            .andExpect(jsonPath("$.errors", hasSize(0)));
        // 400 입력값 오류: 필드별 errors
        postJson("/api/v1/categories", "{\"name\":\"\"}")
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("name"));
        // 400 조회 조건 오류
        getJson("/api/v1/schedules?from=2026-09-01")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        // 405 지원하지 않는 요청
        deleteJson("/api/v1/categories")
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_REQUEST"));
    }
}
