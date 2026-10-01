package com.awesomedesk.j_planner.common.apidocs;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import com.awesomedesk.j_planner.support.OpenApiContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 응답이 명세(08-openapi.yaml)와 맞는지 검사하는 장치 자체가 동작하는지 확인한다.
 * 실제 검사는 모든 통합·인수 테스트의 응답에 자동으로 붙는다 (IntegrationTest → OpenApiContract).
 */
class OpenApiContractTest {

    private static ValidationReport validate(String path, int status, String body) {
        return OpenApiContract.validator().validateResponse(path, Request.Method.GET,
            SimpleResponse.Builder.status(status).withContentType("application/json").withBody(body).build());
    }

    @Test
    @DisplayName("명세와 맞는 응답은 통과")
    void valid() {
        assertThat(validate("/api/v1/health", 200,
            "{\"status\":\"UP\",\"version\":null,\"commit\":null,\"commitTime\":null,\"buildTime\":null}").hasErrors())
            .isFalse();
    }

    @Test
    @DisplayName("필수 필드가 빠진 응답은 오류")
    void missingRequired() {
        assertThat(validate("/api/v1/health", 200, "{\"version\":\"1.0\"}").hasErrors()).isTrue();
    }

    @Test
    @DisplayName("명세에 없는 값(enum 밖)은 오류")
    void enumViolation() {
        assertThat(validate("/api/v1/health", 200,
            "{\"status\":\"DOWN\",\"version\":null,\"commit\":null,\"commitTime\":null,\"buildTime\":null}").hasErrors())
            .isTrue();
    }
}
