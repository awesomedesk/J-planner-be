package com.awesomedesk.j_planner.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** 단위 테스트에서 ApiException(오류 코드·필드)을 확인한다 */
public final class ApiExceptionAssertions {

    private ApiExceptionAssertions() {
    }

    /** VALIDATION_FAILED + errors[0].field 가 field */
    public static void assertFieldError(ThrowingCallable call, String field) {
        ApiException e = catchThrowableOfType(ApiException.class, call);
        assertThat(e).as("ApiException이 나야 한다").isNotNull();
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(e.getErrors()).first().extracting("field").isEqualTo(field);
    }

    public static void assertErrorCode(ThrowingCallable call, ErrorCode code) {
        ApiException e = catchThrowableOfType(ApiException.class, call);
        assertThat(e).as("ApiException이 나야 한다").isNotNull();
        assertThat(e.getErrorCode()).isEqualTo(code);
    }
}
