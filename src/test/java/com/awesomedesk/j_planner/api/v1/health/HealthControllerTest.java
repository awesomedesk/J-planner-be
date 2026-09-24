package com.awesomedesk.j_planner.api.v1.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@org.springframework.context.annotation.Import(com.awesomedesk.j_planner.config.WebConfig.class)
class HealthControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("GET /api/v1/health → 200 {status: UP}")
    void health() throws Exception {
        mvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    @DisplayName("FE 로컬 주소(localhost:3000)의 CORS 사전 요청을 허용한다")
    void corsPreflightFromFeLocal() throws Exception {
        mvc.perform(options("/api/v1/health")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    @DisplayName("허용하지 않은 주소의 CORS 요청은 막는다")
    void corsRejectsOtherOrigin() throws Exception {
        mvc.perform(get("/api/v1/health").header(HttpHeaders.ORIGIN, "http://evil.example.com"))
            .andExpect(status().isForbidden());
    }
}
