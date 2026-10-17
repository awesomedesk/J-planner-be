package com.awesomedesk.j_planner.api.v1.auth.google;

import com.awesomedesk.j_planner.api.v1.auth.EmailAddress;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * 검증이 끝난 구글 ID 토큰에서 쓰는 값 (서명·iss·aud·exp·nonce는 Spring Security가 확인한 뒤).
 *
 * @param sub           구글 사용자 번호 — 이것으로만 구분한다
 * @param email         소문자·앞뒤 공백 정리. 없으면 null
 * @param emailVerified 구글이 이메일 소유를 확인했는가 (새 가입은 true일 때만)
 * @param name          구글 이름 (없을 수 있음)
 */
record GoogleProfile(String sub, String email, boolean emailVerified, String name) {

    static GoogleProfile from(OidcUser user) {
        String email = user.getEmail() == null ? null : EmailAddress.normalize(user.getEmail());
        return new GoogleProfile(user.getSubject(), email == null || email.isEmpty() ? null : email,
            Boolean.TRUE.equals(user.getEmailVerified()), user.getFullName());
    }
}
