package com.awesomedesk.j_planner.api.v1.auth;

/** 유출된 비밀번호인지 (US-33 가입, US-35·37 비밀번호 바꾸기). 구현: {@link HibpBreachedPasswords} */
@FunctionalInterface
public interface BreachedPasswords {

    boolean isBreached(String password);
}
