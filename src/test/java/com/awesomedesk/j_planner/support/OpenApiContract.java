package com.awesomedesk.j_planner.support;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * 명세 자동 대조: 통합·인수 테스트의 모든 응답이 08-openapi.yaml(빌드 때 static/openapi.yaml로 복사)과 맞는지 검사한다.
 * - 요청은 검사하지 않는다 (잘못된 요청을 일부러 보내는 테스트가 있으므로). 응답만 검사
 * - 명세에 없는 주소(테스트 전용 등)는 건너뛴다
 */
@TestConfiguration
public class OpenApiContract {

    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
        .createForSpecificationUrl("static/openapi.yaml")
        .withBasePathOverride("/api/v1")
        .withLevelResolver(LevelResolver.create()
            .withLevel("validation.request", ValidationReport.Level.IGNORE)
            .withLevel("validation.response.path.missing", ValidationReport.Level.IGNORE)
            .build())
        .build();

    public static OpenApiInteractionValidator validator() {
        return VALIDATOR;
    }

    @Bean
    MockMvcBuilderCustomizer openApiResponseValidation() {
        return builder -> builder.alwaysExpect(OpenApiValidationMatchers.openApi().isValid(VALIDATOR));
    }
}
