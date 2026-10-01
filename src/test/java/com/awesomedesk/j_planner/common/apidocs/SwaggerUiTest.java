package com.awesomedesk.j_planner.common.apidocs;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 사람이 보는 API 명세 화면 (대표님 결정 2026-10-01).
 * 명세는 코드에서 만들지 않고 j-planner-product/08-openapi.yaml을 그대로 띄운다 (명세 먼저, contract-first).
 */
class SwaggerUiTest extends IntegrationTest {

    @Test
    @DisplayName("명세 파일 /openapi.yaml = 08-openapi.yaml (OpenAPI 3.1, 모든 API 포함)")
    void specFile() throws Exception {
        mvc.perform(get("/openapi.yaml"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("openapi: 3.1")))
            .andExpect(content().string(containsString("/dday-marks:")))
            .andExpect(content().string(containsString("/settings:")));
    }

    @Test
    @DisplayName("Swagger UI 화면이 열리고, 그 명세 파일을 보여주도록 설정돼 있다")
    void swaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/openapi.yaml")));
    }
}
