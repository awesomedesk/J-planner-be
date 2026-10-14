package com.awesomedesk.j_planner.api.v1.me;

import java.util.List;

/** 내 정보 (08 13-4). 로그인 응답도 같은 모양 */
public record MeResponse(String email, String name, boolean hasPassword, List<LoginMethod> loginMethods) {
}
