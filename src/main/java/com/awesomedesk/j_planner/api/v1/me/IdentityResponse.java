package com.awesomedesk.j_planner.api.v1.me;

import com.awesomedesk.j_planner.api.v1.auth.google.UserIdentities;
import java.time.LocalDateTime;

/** 연결된 로그인 수단 {@code GET /me/identities} (08 13-4). email = 연결할 때의 구글 이메일 */
public record IdentityResponse(String provider, String email, LocalDateTime linkedAt) {

    static IdentityResponse from(UserIdentities.Identity identity) {
        return new IdentityResponse(identity.provider(), identity.email() == null ? "" : identity.email(), identity.linkedAt());
    }
}
