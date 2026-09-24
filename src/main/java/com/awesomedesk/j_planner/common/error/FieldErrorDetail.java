package com.awesomedesk.j_planner.common.error;

/**
 * 입력값 오류의 필드별 내용. Problem Details 응답의 {@code errors} 배열 항목.
 *
 * @param field   오류가 난 필드 이름 (예: {@code title}, {@code time.durationMinutes})
 * @param message 사용자에게 보여줄 설명
 */
public record FieldErrorDetail(String field, String message) {
}
