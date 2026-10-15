package com.awesomedesk.j_planner.api.v1.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 메일 링크 토큰 (07 11-2): 32바이트 무작위 → base64url(패딩 없음, 43자). 원문은 메일에만, DB에는 {@link #hash} (SHA-256 32바이트).
 * 무작위 값이 충분히 길어서 소금 없이 SHA-256으로 충분하다 (비밀번호처럼 추측할 수 없음).
 */
public final class SignupTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SignupTokens() {
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
