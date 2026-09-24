package com.awesomedesk.j_planner.common.error;

import java.util.List;
import lombok.Getter;

/**
 * 서비스 계층에서 던지는 API 예외. {@link GlobalExceptionHandler}가 Problem Details(RFC 9457)로 바꾼다.
 *
 * <pre>
 * throw ApiException.notFound("Todo를 찾을 수 없습니다: " + id);
 * throw new ApiException(ErrorCode.CATEGORY_NAME_DUPLICATED, "같은 이름의 카테고리가 이미 있습니다: " + name);
 * </pre>
 *
 * {@code getMessage()}는 응답의 {@code detail}로 그대로 나가므로 사용자에게 보여줘도 되는 한국어로 쓴다.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldErrorDetail> errors;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultDetail());
    }

    public ApiException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, List.of());
    }

    public ApiException(ErrorCode errorCode, String detail, List<FieldErrorDetail> errors) {
        super(detail);
        this.errorCode = errorCode;
        this.errors = List.copyOf(errors);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(ErrorCode.NOT_FOUND, detail);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultDetail(),
            List.of(new FieldErrorDetail(field, message)));
    }

    public static ApiException invalidQuery(String detail) {
        return new ApiException(ErrorCode.INVALID_QUERY, detail);
    }
}
