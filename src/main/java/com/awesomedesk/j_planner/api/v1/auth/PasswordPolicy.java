package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.common.error.FieldErrorDetail;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 규칙 (11 문서 3-2, 08 13-3·13-6): <b>15~128자</b>(글자 수 = 코드 포인트), 조합 규칙 없음, 유출 비밀번호 차단.
 * 어기면 400 PASSWORD_REJECTED (errors에 password). 가입(US-33)·재설정(US-35)·변경(US-37)이 같이 쓴다.
 */
@Component
public class PasswordPolicy {

    static final int MIN = 15;
    static final int MAX = 128;

    private final BreachedPasswords breached;

    public PasswordPolicy(BreachedPasswords breached) {
        this.breached = breached;
    }

    public void check(String password) {
        if (password == null) {
            throw ApiException.validation("password", "비밀번호를 입력해 주세요.");
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN) {
            throw rejected("비밀번호는 " + MIN + "자 이상이어야 해요.");
        }
        if (length > MAX) {
            throw rejected("비밀번호는 " + MAX + "자까지 쓸 수 있어요.");
        }
        if (breached.isBreached(password)) {
            throw rejected("유출된 적이 있는 비밀번호예요. 다른 비밀번호를 써 주세요.");
        }
    }

    private static ApiException rejected(String message) {
        return new ApiException(ErrorCode.PASSWORD_REJECTED, ErrorCode.PASSWORD_REJECTED.getDefaultDetail(),
            List.of(new FieldErrorDetail("password", message)));
    }
}
