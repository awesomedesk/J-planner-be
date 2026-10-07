package com.awesomedesk.j_planner.common.auth;

import java.security.Principal;

/**
 * 로그인한 회원 (US-32, 08-api-design.md 13-2). 요청의 {@code getUserPrincipal()}로 전달된다.
 * 컨트롤러는 파라미터로 받는다 ({@link AuthUserArgumentResolver}). userId는 요청(주소·본문·쿼리)에서 받지 않는다 (SEC-02).
 *
 * @param userId 회원번호 (users.user_id)
 */
public record AuthUser(long userId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
