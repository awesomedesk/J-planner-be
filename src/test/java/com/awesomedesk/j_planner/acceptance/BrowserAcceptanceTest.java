package com.awesomedesk.j_planner.acceptance;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.awesomedesk.j_planner.support.OpenApiContract;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 실제 브라우저처럼 쿠키를 주고받는 인수 테스트의 부모 (US-33·34).
 * 기본 로그인·CSRF가 붙은 {@code mvc} 대신 기본값 없는 MockMvc(세션 필터 + Spring Security + 명세 대조)를 쓴다.
 */
abstract class BrowserAcceptanceTest extends AcceptanceTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSessionRepositoryFilter")
    private Filter sessionFilter;

    /** 기본값 없는 MockMvc */
    protected MockMvc web;

    @BeforeEach
    void setUpWeb() {
        web = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(sessionFilter)
            .apply(springSecurity())
            .alwaysExpect(OpenApiValidationMatchers.openApi().isValid(OpenApiContract.validator()))
            .build();
    }

    /** 새 브라우저 (쿠키 없음) */
    protected Browser browser(String ip) {
        return new Browser(web, ip);
    }

    /** 새 브라우저로 로그인까지 */
    protected Browser loggedIn(String email, String password, String ip) throws Exception {
        Browser b = browser(ip).withCsrf();
        b.login(email, password).andExpect(status().isOk());
        return b;
    }
}
