package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.api.v1.me.LoginMethod;
import com.awesomedesk.j_planner.api.v1.me.MeResponse;
import java.util.List;

/**
 * 이메일 로그인 응답 (08 13-3) = 내 정보 + {@code restored}.
 *
 * @param restored 이번 로그인으로 휴면에서 복원됐으면 true — FE가 '휴면에서 돌아왔어요' 안내 (US-38)
 */
public record LoginResponse(String email, String name, boolean hasPassword, List<LoginMethod> loginMethods, boolean restored) {

    static LoginResponse of(MeResponse me, boolean restored) {
        return new LoginResponse(me.email(), me.name(), me.hasPassword(), me.loginMethods(), restored);
    }
}
