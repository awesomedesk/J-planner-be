package com.awesomedesk.j_planner.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 오류 코드. Problem Details 응답의 {@code code} 속성으로 나간다.
 * 목록·의미는 j-planner-product/08-api-design.md 2-6절과 같게 유지한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인하세요."),
    INVALID_QUERY(HttpStatus.BAD_REQUEST, "조회 조건을 확인하세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
    CATEGORY_NAME_DUPLICATED(HttpStatus.CONFLICT, "같은 이름의 카테고리가 이미 있습니다."),
    DEFAULT_CATEGORY_LOCKED(HttpStatus.CONFLICT, "'미지정' 카테고리는 이름을 바꾸거나 삭제·이동할 수 없습니다."),
    /** 405·406·415 등 요청 방식 오류. 실제 상태 코드는 원인 예외를 따른다. */
    UNSUPPORTED_REQUEST(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도하세요.");

    private final HttpStatus status;
    private final String defaultDetail;
}
