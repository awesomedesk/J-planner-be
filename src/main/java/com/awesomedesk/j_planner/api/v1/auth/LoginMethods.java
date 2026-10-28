package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.api.v1.auth.google.UserIdentities;
import com.awesomedesk.j_planner.api.v1.me.LoginMethod;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * '이미 가입된 이메일' 409 안내의 로그인 방법 (08 13-3, D-076 3번) — 가입(US-33)과 이메일 변경(US-37)이 같이 쓴다.
 * EMAIL = 비밀번호가 있거나 구글도 없는 이메일 계정(비밀번호가 아직 없는 admin도 [로그인]/[비밀번호 찾기]), GOOGLE = 구글 연결 있음.
 * 구글로 가입해 비밀번호가 없으면 GOOGLE만.
 */
@Component
@RequiredArgsConstructor
public class LoginMethods {

    private final PasswordCredentials passwordCredentials;
    private final UserIdentities identities;

    public List<String> forConflict(long userId) {
        boolean google = identities.hasGoogle(userId);
        List<String> methods = new ArrayList<>();
        if (passwordCredentials.exists(userId) || !google) {
            methods.add(LoginMethod.EMAIL.name());
        }
        if (google) {
            methods.add(LoginMethod.GOOGLE.name());
        }
        return methods;
    }
}
