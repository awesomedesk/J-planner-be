package com.awesomedesk.j_planner.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.api.v1.health.HealthController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US-44 · PO #398 ①: 운영(prd)에서는 정적 API 명세 {@code /openapi.yaml}도 404로 숨긴다.
 * 로컬·테스트는 그대로 연다 (Swagger UI·응답 대조). FE 타입 생성은 product 저장소의 08-openapi.yaml을 쓰므로 영향 없다.
 * <p>
 * 방법: prd에서 정적 파일 제공을 끈다 ({@code spring.web.resources.add-mappings: false}) — 이 서버의 정적 파일은 명세 하나뿐이다.
 */
@DisplayName("US-44 운영에서 API 명세 숨김")
class ApiSpecExposureTest {

    @Test
    @DisplayName("prd 설정: 정적 파일 제공 끔 + Swagger UI·api-docs 끔")
    void prdTurnsOffStaticAndSwagger() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
            .load("prd", new ClassPathResource("profile/application-prd.yml"));
        PropertySource<?> prd = sources.stream()
            .filter(s -> "prd".equals(s.getProperty("spring.config.activate.on-profile")))
            .findFirst().orElseThrow();
        assertThat(prd.getProperty("spring.web.resources.add-mappings")).isEqualTo(false);
        assertThat(prd.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(prd.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
    }

    @Nested
    @DisplayName("정적 파일 제공을 끄면 (prd)")
    @AutoConfigureMockMvc(addFilters = false)
    @WebMvcTest(controllers = HealthController.class, properties = "spring.web.resources.add-mappings=false")
    class Hidden {

        @Autowired
        MockMvc mvc;

        @Test
        @DisplayName("GET /openapi.yaml → 404 NOT_FOUND (Problem Details), 명세 내용 없음")
        void notFound() throws Exception {
            mvc.perform(get("/openapi.yaml"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("기본 설정 (local·tst·테스트)")
    @AutoConfigureMockMvc(addFilters = false)
    @WebMvcTest(controllers = HealthController.class)
    class Shown {

        @Autowired
        MockMvc mvc;

        @Test
        @DisplayName("GET /openapi.yaml → 200, 명세 파일")
        void served() throws Exception {
            mvc.perform(get("/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("openapi:")));
        }
    }
}
