package com.awesomedesk.j_planner.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.awesomedesk.j_planner.common.auth.AuthUser;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * 테스트 요청은 기본으로 admin 계정(user_id = 1, reset.sql)이 로그인한 것으로 보낸다 (US-32).
 * 다른 회원으로 보내려면 {@link IntegrationTest#as(long)}, 로그인 없이 보내려면 {@link IntegrationTest#anonymous()}.
 */
@TestConfiguration
public class LoginUserConfig {

    public static final long ADMIN_ID = 1L;

    @Bean
    MockMvcBuilderCustomizer loginAsAdmin() {
        return builder -> builder.defaultRequest(get("/").principal(new AuthUser(ADMIN_ID)));
    }
}
