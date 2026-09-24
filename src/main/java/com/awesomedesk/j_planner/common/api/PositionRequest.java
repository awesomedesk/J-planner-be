package com.awesomedesk.j_planner.common.api;

/**
 * 순서 이동 요청 (08-api-design.md 2-5절). {@code afterId} 바로 뒤로 옮긴다. {@code null} = 맨 앞.
 */
public record PositionRequest(Long afterId) {
}
