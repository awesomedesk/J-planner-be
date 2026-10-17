package com.awesomedesk.j_planner.api.v1.auth;

import java.util.Locale;
import java.util.regex.Pattern;

/** 가입·다시 보내기·구글 가입(US-36)에서 받는 이메일: 앞뒤 공백을 빼고 소문자로 (07 11-2 users.email 규칙), 254자까지, 모양 확인 */
public final class EmailAddress {

    public static final int MAX = 254;
    /** 로컬@도메인.끝 — 공백·@ 하나만. 실제로 받을 수 있는지는 인증 메일로 확인한다 */
    private static final Pattern SHAPE = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@.]+$");

    private EmailAddress() {
    }

    /** 정리한 값. 비었으면 빈 문자열 */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String normalized) {
        return !normalized.isEmpty() && normalized.length() <= MAX && SHAPE.matcher(normalized).matches();
    }
}
