package com.awesomedesk.j_planner.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.awesomedesk.j_planner.common.auth.AuthUser;
import com.awesomedesk.j_planner.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
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
    static final String TEST_CSRF_TOKEN = "test-csrf-token";

    @Bean
    MockMvcBuilderCustomizer loginAsAdmin() {
        // CSRF는 실제와 같게 쿠키 + 같은 값의 헤더로. (spring-security-test의 csrf()는 공유 필터의 토큰 저장소를
        //  세션 방식으로 바꿔 버려서, 쿠키 흐름을 보는 US-34 테스트를 깨뜨린다)
        return builder -> builder.defaultRequest(get("/")
            .cookie(new Cookie(SecurityConfig.CSRF_COOKIE, TEST_CSRF_TOKEN))
            .header("X-XSRF-TOKEN", TEST_CSRF_TOKEN)
            .with(authentication(new AuthUser(ADMIN_ID).toAuthentication())));
    }
}
