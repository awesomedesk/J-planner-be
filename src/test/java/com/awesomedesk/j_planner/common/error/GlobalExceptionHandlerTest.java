package com.awesomedesk.j_planner.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오류 응답이 Problem Details(RFC 9457) + code·errors 모양인지 확인한다 (08-api-design.md 2-6절).
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @RestController
    static class TestController {

        @GetMapping("/test/api-exception")
        void apiException() {
            throw new ApiException(ErrorCode.CATEGORY_NAME_DUPLICATED, "같은 이름의 카테고리가 이미 있습니다: 공부");
        }

        @GetMapping("/test/query")
        String query(@RequestParam LocalDate from) {
            return "ok";
        }

        @PostMapping("/test/body")
        String body(@RequestBody Map<String, Object> body) {
            return "ok";
        }

        record TypedBody(Integer count, LocalDate date) {
        }

        @PostMapping("/test/typed")
        String typed(@RequestBody TypedBody body) {
            return "ok";
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new DataIntegrityViolationException("Duplicate entry '2026-09-28' for key 'diaries.uk_diaries_active_date'");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret internal message");
        }
    }

    @Test
    @DisplayName("ApiException → 해당 상태 코드, code, detail, instance, 빈 errors")
    void apiException() throws Exception {
        mvc.perform(get("/test/api-exception"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.code").value("CATEGORY_NAME_DUPLICATED"))
            .andExpect(jsonPath("$.detail").value("같은 이름의 카테고리가 이미 있습니다: 공부"))
            .andExpect(jsonPath("$.instance").value("/test/api-exception"))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터 없음 → 400 INVALID_QUERY, errors에 파라미터 이름")
    void missingQueryParam() throws Exception {
        mvc.perform(get("/test/query"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"))
            .andExpect(jsonPath("$.errors[0].field").value("from"));
    }

    @Test
    @DisplayName("쿼리 파라미터 형식 오류 → 400 INVALID_QUERY")
    void badQueryParamFormat() throws Exception {
        mvc.perform(get("/test/query").param("from", "2026-13-99"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"))
            .andExpect(jsonPath("$.errors[0].field").value("from"));
    }

    @Test
    @DisplayName("JSON 형식 오류 → 400 VALIDATION_FAILED")
    void malformedJson() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    @DisplayName("본문 값의 형식이 틀림(숫자 자리에 글자 등) → 400 VALIDATION_FAILED, errors에 필드 이름")
    void wrongValueType() throws Exception {
        mvc.perform(post("/test/typed").contentType(MediaType.APPLICATION_JSON).content("{\"count\":\"많이\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("count"));
    }

    @Test
    @DisplayName("없는 메서드 → 405 UNSUPPORTED_REQUEST")
    void methodNotAllowed() throws Exception {
        mvc.perform(post("/test/boom"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_REQUEST"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type → 415 UNSUPPORTED_REQUEST")
    void unsupportedMediaType() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("hello"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_REQUEST"));
    }

    @Test
    @DisplayName("동시 저장 등으로 DB 제약에 걸림 → 409 CONFLICT, SQL 내용은 응답에 없음")
    void dataIntegrityViolation() throws Exception {
        mvc.perform(get("/test/conflict"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.detail", not(containsString("Duplicate"))))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    @DisplayName("처리하지 않은 예외 → 500 INTERNAL_ERROR, 내부 메시지는 응답에 없음")
    void unexpectedException() throws Exception {
        mvc.perform(get("/test/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.detail", not(containsString("secret"))));
    }
}
