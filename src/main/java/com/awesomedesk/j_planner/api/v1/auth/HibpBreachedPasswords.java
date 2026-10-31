package com.awesomedesk.j_planner.api.v1.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * 유출 비밀번호 확인 — HIBP Pwned Passwords k-익명성 API (11 문서 3-2, 12 문서. 키 없이 무료).
 * <ol>
 *   <li>비밀번호 SHA-1(대문자 16진수 40글자)의 <b>앞 5글자만</b> 보낸다 ({@code GET https://api.pwnedpasswords.com/range/<5글자>}, {@code Add-Padding: true})</li>
 *   <li>돌아온 {@code 뒤 35글자:횟수} 줄 목록에 내 뒤 35글자가 있고 횟수가 1 이상이면 유출. 횟수 0은 덧붙인 가짜 줄</li>
 * </ol>
 * 비밀번호 원문·전체 해시는 밖으로 나가지 않는다.
 * HIBP에 닿지 못하면(오류·시간 초과) <b>막지 않고</b> 경고 로그만 남긴다 — 외부 서비스 장애로 가입·비밀번호 변경이 멈추지 않게 (BE가 정한 부분, PO 확인 요청).
 */
@Slf4j
public class HibpBreachedPasswords implements BreachedPasswords {

    private final Function<String, String> rangeFetcher;
    private final Runnable onUnreachable;

    /** @param rangeFetcher SHA-1 앞 5글자 → 응답 본문 */
    public HibpBreachedPasswords(Function<String, String> rangeFetcher) {
        this(rangeFetcher, () -> {
        });
    }

    /**
     * @param rangeFetcher  SHA-1 앞 5글자 → 응답 본문
     * @param onUnreachable HIBP에 닿지 못해 확인 없이 진행할 때마다 (지표 jp_auth_breach_check_failed_total, US-41)
     */
    public HibpBreachedPasswords(Function<String, String> rangeFetcher, Runnable onUnreachable) {
        this.rangeFetcher = rangeFetcher;
        this.onUnreachable = onUnreachable;
    }

    @Override
    public boolean isBreached(String password) {
        String sha1 = sha1Hex(password);
        String prefix = sha1.substring(0, 5);
        String suffix = sha1.substring(5);
        String body;
        try {
            body = rangeFetcher.apply(prefix);
        } catch (RuntimeException e) {
            log.warn("유출 비밀번호 확인(HIBP) 실패 — 막지 않고 진행: {}", e.toString());
            onUnreachable.run();
            return false;
        }
        if (body == null) {
            return false;
        }
        for (String line : body.split("\\R")) {
            int colon = line.indexOf(':');
            if (colon < 0 || !line.substring(0, colon).trim().equalsIgnoreCase(suffix)) {
                continue;
            }
            try {
                return Long.parseLong(line.substring(colon + 1).trim()) > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    static String sha1Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
