package com.awesomedesk.j_planner.common.error;

import java.util.List;
import lombok.Getter;

/**
 * 가입하려는 이메일이 이미 회원 → 409 EMAIL_ALREADY_REGISTERED + {@code loginMethods} (08 13-3, D-061).
 * FE가 로그인 방법에 맞춰 [로그인]/[비밀번호 찾기] 또는 [구글로 로그인]을 보여준다.
 */
@Getter
public class EmailAlreadyRegisteredException extends ApiException {

    /** EMAIL / GOOGLE (LoginMethod 이름) */
    private final List<String> loginMethods;

    public EmailAlreadyRegisteredException(List<String> loginMethods) {
        super(ErrorCode.EMAIL_ALREADY_REGISTERED);
        this.loginMethods = List.copyOf(loginMethods);
    }
}
