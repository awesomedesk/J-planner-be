package com.awesomedesk.j_planner.common.auth;

import java.io.Serializable;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 로그인한 회원 (US-32·34, 08-api-design.md 13-2). 로그인하면 세션의 SecurityContext에 이 값이 들어간다.
 * 컨트롤러는 파라미터로 받는다 ({@link AuthUserArgumentResolver}). userId는 요청(주소·본문·쿼리)에서 받지 않는다 (SEC-02).
 * {@code java.security.Principal}이 아니라 {@link AuthenticatedPrincipal}인 이유: Principal이면 Spring MVC 기본 처리기가 먼저 가져가 버린다.
 * {@link #getName()} = user_id 문자열 — Spring Session의 '회원별 세션 찾기'(모든 기기 로그아웃, 어드민 세션 삭제) 기준
 *
 * @param userId 회원번호 (users.user_id)
 */
public record AuthUser(long userId) implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }

    /** Spring Security 인증 정보 (권한은 일반 회원 하나) */
    public Authentication toAuthentication() {
        return UsernamePasswordAuthenticationToken.authenticated(this, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
