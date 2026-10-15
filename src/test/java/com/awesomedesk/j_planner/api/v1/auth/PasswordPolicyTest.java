package com.awesomedesk.j_planner.api.v1.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** US-33 AC1 비밀번호 규칙 (08 13-3·13-6): 15~128자(글자 수 = 코드 포인트), 조합 규칙 없음, 유출 비밀번호 차단 — DB 없이 */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(Set.of("leaked-password-123")::contains);

    @Test
    @DisplayName("15자 미만은 PASSWORD_REJECTED (errors에 password), 15자부터 된다")
    void minimum15() {
        assertRejected("a".repeat(14));
        assertThatCode(() -> policy.check("a".repeat(15))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("128자까지 되고 129자는 PASSWORD_REJECTED")
    void maximum128() {
        assertThatCode(() -> policy.check("a".repeat(128))).doesNotThrowAnyException();
        assertRejected("a".repeat(129));
    }

    @Test
    @DisplayName("글자 수는 눈에 보이는 글자(코드 포인트) 기준 — 한글·이모지 한 글자 = 1자")
    void countsCodePoints() {
        assertThatCode(() -> policy.check("가나다라마바사아자차카타파하가")).doesNotThrowAnyException(); // 15자
        assertRejected("😀".repeat(14)); // UTF-16으로는 28칸이지만 14자
        assertThatCode(() -> policy.check("😀".repeat(15))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("조합 규칙 없음 — 숫자·특수문자 없이 소문자만, 공백 포함도 된다")
    void noCompositionRules() {
        assertThatCode(() -> policy.check("correct horse battery")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("유출된 비밀번호는 PASSWORD_REJECTED")
    void breached() {
        assertRejected("leaked-password-123");
    }

    @Test
    @DisplayName("비밀번호가 없으면(null) VALIDATION_FAILED (errors에 password)")
    void missing() {
        ApiException e = catchThrowableOfType(ApiException.class, () -> policy.check(null));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(e.getErrors()).first().extracting("field").isEqualTo("password");
    }

    private void assertRejected(String password) {
        ApiException e = catchThrowableOfType(ApiException.class, () -> policy.check(password));
        assertThat(e).as("ApiException이 나야 한다").isNotNull();
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_REJECTED);
        assertThat(e.getErrors()).first().extracting("field").isEqualTo("password");
    }
}
