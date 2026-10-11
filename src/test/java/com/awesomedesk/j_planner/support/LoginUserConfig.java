package com.awesomedesk.j_planner.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.awesomedesk.j_planner.common.auth.AuthUser;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * 테스트 요청은 기본으로 admin 계정(user_id = 1, reset.sql)이 로그인한 것으로, CSRF 토큰을 붙여 보낸다 (US-32·34).
 * 다른 회원으로 보내려면 {@link IntegrationTest#as(long)}, 로그인 없이 보내려면 {@link IntegrationTest#anonymous()}.
 * 실제 로그인·세션·CSRF 흐름은 US34LoginAcceptanceTest가 기본값 없는 MockMvc로 확인한다.
 */
@TestConfiguration
public class LoginUserConfig {

    public static final long ADMIN_ID = 1L;

    @Bean
    MockMvcBuilderCustomizer loginAsAdmin() {
        return builder -> builder.defaultRequest(get("/").with(csrf()).with(authentication(new AuthUser(ADMIN_ID).toAuthentication())));
    }
}
