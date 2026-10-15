package com.awesomedesk.j_planner.acceptance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 쿠키(세션 __Host-JP_SID, CSRF __Host-XSRF-TOKEN)를 기억하는 브라우저 하나 (기기 하나).
 * 기본 로그인·CSRF가 없는 MockMvc({@link BrowserAcceptanceTest#web})로 실제 흐름을 본다 (US-33·34).
 */
final class Browser {

    static final String CSRF = "/api/v1/auth/csrf";
    static final String LOGIN = "/api/v1/auth/login";
    static final String SESSION_COOKIE = "__Host-JP_SID";
    static final String XSRF_COOKIE = "__Host-XSRF-TOKEN";

    private final MockMvc web;
    final String ip;
    Cookie sid;
    String xsrf;

    Browser(MockMvc web, String ip) {
        this.web = web;
        this.ip = ip;
    }

    /** 앱을 열 때처럼 CSRF 쿠키를 받는다 */
    Browser withCsrf() throws Exception {
        MockHttpServletResponse response = web.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(CSRF).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        })).andExpect(status().isNoContent()).andReturn().getResponse();
        this.xsrf = response.getCookie(XSRF_COOKIE).getValue();
        return this;
    }

    ResultActions login(String email, String password) throws Exception {
        return postJson(LOGIN, "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    ResultActions postJson(String url, String json) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions get(String url) throws Exception {
        return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url));
    }

    ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        List<Cookie> cookies = new ArrayList<>();
        if (sid != null) {
            cookies.add(sid);
        }
        if (xsrf != null) {
            cookies.add(new Cookie(XSRF_COOKIE, xsrf));
            request.header("X-XSRF-TOKEN", xsrf);
        }
        if (!cookies.isEmpty()) {
            request.cookie(cookies.toArray(Cookie[]::new));
        }
        request.header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh) Chrome/141.0");
        ResultActions result = web.perform(request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }));
        Cookie newSid = result.andReturn().getResponse().getCookie(SESSION_COOKIE);
        if (newSid != null) {
            sid = newSid.getMaxAge() == 0 ? null : newSid;
        }
        return result;
    }
}
