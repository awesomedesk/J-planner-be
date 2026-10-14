package com.awesomedesk.j_planner.common.log;

import java.util.Locale;

/**
 * 로그·운영 화면에 쓰는 이메일 가림 (08-api-design.md 13-8 예시표, 어드민과 같은 규칙).
 * <ul>
 *   <li>로컬: 앞 2글자 + {@code ***}. 로컬이 2글자 이하면 첫 글자 + {@code ***}</li>
 *   <li>도메인: 첫 글자 + {@code ***} + 맨 끝 마디(예: {@code .com}, {@code .kr}). 점이 없으면 첫 글자 + {@code ***}</li>
 *   <li>모양이 이메일이 아니면 {@code ***}. 소문자로 바꿔서 가린다</li>
 * </ul>
 * 예: {@code abcdef@gmail.com → ab***@g***.com}, {@code hong.gil@mail.co.kr → ho***@m***.kr}
 */
public final class EmailMasker {

    private static final String STARS = "***";

    private EmailMasker() {
    }

    public static String mask(String email) {
        if (email == null) {
            return STARS;
        }
        String value = email.strip().toLowerCase(Locale.ROOT);
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            return STARS;
        }
        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        String maskedLocal = (local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2)) + STARS;
        int lastDot = domain.lastIndexOf('.');
        String maskedDomain = domain.charAt(0) + STARS + (lastDot > 0 ? domain.substring(lastDot) : "");
        return maskedLocal + "@" + maskedDomain;
    }
}
