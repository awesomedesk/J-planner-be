package com.awesomedesk.j_planner.support;

import com.awesomedesk.j_planner.api.v1.auth.BreachedPasswords;
import java.util.Set;

/** 테스트용 유출 비밀번호 목록 (실제 HIBP를 부르지 않는다, US-33) */
public class FakeBreachedPasswords implements BreachedPasswords {

    /** 유출된 것으로 치는 비밀번호 (15자 이상) */
    public static final Set<String> LEAKED = Set.of("password1234567890", "qwertyuiopasdfghjkl");

    @Override
    public boolean isBreached(String password) {
        return LEAKED.contains(password);
    }
}
