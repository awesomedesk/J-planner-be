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
    /** 로그인 안 함 / 세션 만료 (08-api-design.md 13-6) */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    /** CSRF 토큰 없음·틀림 (08 13-1) */
    CSRF_INVALID(HttpStatus.FORBIDDEN, "보안 토큰이 없거나 맞지 않습니다. 새로고침한 뒤 다시 시도하세요."),
    /** 민감 작업(구글 연결·해제, 이메일·비밀번호 변경, 탈퇴)인데 재인증한 지 5분이 지남 (08 13-4) */
    REAUTH_REQUIRED(HttpStatus.FORBIDDEN, "보안을 위해 비밀번호를 다시 확인해 주세요."),
    /** 재인증 비밀번호가 틀림·비밀번호 없는 회원. 로그인 만료(401)와 구별해 FE가 '다시 로그인' 창을 띄우지 않게 (D-085). errors에 password */
    REAUTH_FAILED(HttpStatus.BAD_REQUEST, "비밀번호가 맞지 않아요"),
    /** 운영자가 정지한 계정. 비밀번호가 맞을 때만 알려준다 (08 13-3) */
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 제한된 계정이에요."),
    /** 15자 미만·128자 초과·유출 비밀번호 (08 13-6). errors에 password */
    PASSWORD_REJECTED(HttpStatus.BAD_REQUEST, "쓸 수 없는 비밀번호예요."),
    /** 메일 링크가 없거나 만료·이미 사용됨 — 구분하지 않는다 (08 13-6) */
    TOKEN_INVALID(HttpStatus.BAD_REQUEST, "링크가 만료됐거나 이미 사용됐어요. 처음부터 다시 해 주세요."),
    /** 구글 가입 확인 화면(D-083)의 대기 정보가 없음 — 10분 지남·다른 브라우저·이미 가입함. 구글로 처음부터 */
    GOOGLE_SIGNUP_EXPIRED(HttpStatus.BAD_REQUEST, "시간이 지나 다시 해야 해요. 구글로 다시 시작해 주세요."),
    /** 가입하려는 이메일이 이미 회원. 응답에 loginMethods (08 13-3) */
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 이메일이에요"),
    /** 마지막 로그인 수단은 해제할 수 없다 — 비밀번호 없이 구글만 있는 계정의 구글 해제 (08 13-4) */
    LAST_IDENTITY(HttpStatus.CONFLICT, "마지막 로그인 수단이라 해제할 수 없어요. 비밀번호를 먼저 만들어 주세요."),
    /** 이미 구글이 연결된 계정에 다시 연결 — 회원당 구글 하나 (07 11-2 user_identities) */
    IDENTITY_ALREADY_CONNECTED(HttpStatus.CONFLICT, "이미 구글이 연결돼 있어요. 해제한 뒤 다시 연결해 주세요."),
    /** 요청 횟수 제한 (08 13-7). Retry-After 헤더(초)와 함께 */
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도하세요."),
    /** 요청 본문이 1MB를 넘음 — 본문을 읽지 않는다 (US-41) */
    CONTENT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "요청이 너무 커요. 내용을 줄여 주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
    CATEGORY_NAME_DUPLICATED(HttpStatus.CONFLICT, "같은 이름의 카테고리가 이미 있습니다."),
    DEFAULT_CATEGORY_LOCKED(HttpStatus.CONFLICT, "'미지정' 카테고리는 이름·색을 바꾸거나 삭제·이동할 수 없습니다."),
    /** 동시에 저장하는 등으로 DB 제약에 걸림. 다시 시도하면 대개 된다 */
    CONFLICT(HttpStatus.CONFLICT, "다른 요청과 겹쳐 저장하지 못했습니다. 다시 시도하세요."),
    /** 405·406·415 등 요청 방식 오류. 실제 상태 코드는 원인 예외를 따른다. */
    UNSUPPORTED_REQUEST(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도하세요.");

    private final HttpStatus status;
    private final String defaultDetail;
}
