package com.awesomedesk.j_planner.api.v1.me;

/** 이름 변경 {@code PATCH /me} (US-37). 없으면 그대로 */
public record MeUpdateRequest(String name) {
}
