package com.awesomedesk.j_planner.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로컬 개발 전용 임시 로그인 (08-api-design.md 13-2, US-32~34 사이).
 * 로그인 API(US-34)가 생기기 전까지 {@code app.auth.dev-user-email} 회원(admin)이 로그인한 것으로 본다.
 * <b>local 프로필에서만</b> 켜진다 (운영·테스트 서버, 자동 테스트에서는 꺼짐). US-34에서 지운다.
 */
@Slf4j
@Component
@Profile("local")
public class DevLoginFilter extends OncePerRequestFilter {

    private final JdbcTemplate jdbc;
    private final String email;
    private volatile AuthUser user;

    public DevLoginFilter(JdbcTemplate jdbc, @Value("${app.auth.dev-user-email:}") String email) {
        this.jdbc = jdbc;
        this.email = email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        AuthUser login = devUser();
        if (login == null || request.getUserPrincipal() != null) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public Principal getUserPrincipal() {
                return login;
            }
        }, response);
    }

    /** 처음 찾을 때 한 번만 DB에서 읽는다. 없으면 로그인 안 한 것(401)으로 둔다 */
    private AuthUser devUser() {
        if (user == null && !email.isEmpty()) {
            List<Long> ids = jdbc.queryForList("SELECT user_id FROM users WHERE email = ?", Long.class, email);
            if (ids.isEmpty()) {
                log.warn("app.auth.dev-user-email 회원이 users에 없습니다. 데이터 API는 401입니다.");
                return null;
            }
            user = new AuthUser(ids.getFirst());
        }
        return user;
    }
}
