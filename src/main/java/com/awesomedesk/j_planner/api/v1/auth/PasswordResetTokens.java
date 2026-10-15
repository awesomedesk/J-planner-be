package com.awesomedesk.j_planner.api.v1.auth;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호 재설정 토큰 — auth_tokens의 PASSWORD_RESET (07 11-2, V5) */
@Component
@RequiredArgsConstructor
class PasswordResetTokens {

    static final String PURPOSE = "PASSWORD_RESET";

    private final JdbcTemplate jdbc;
    private final PasswordCredentials passwordCredentials;

    /** 새 토큰을 넣고 그 회원의 이전 재설정 토큰은 모두 무효(used_at)로 */
    @Transactional
    void issue(long userId, byte[] tokenHash, LocalDateTime now, LocalDateTime expiresAt) {
        jdbc.update("UPDATE auth_tokens SET used_at = ? WHERE user_id = ? AND purpose = ? AND used_at IS NULL", now, userId, PURPOSE);
        jdbc.update("INSERT INTO auth_tokens (user_id, purpose, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?, ?)",
            userId, PURPOSE, tokenHash, expiresAt, now);
    }

    /** 쓸 수 있는 토큰이면 회원 번호. 없거나 만료·사용됨이면 null (쓰지 않고 보기만) */
    Long findUsable(byte[] tokenHash, LocalDateTime now) {
        List<Long> found = jdbc.queryForList(
            "SELECT user_id FROM auth_tokens WHERE token_hash = ? AND purpose = ? AND used_at IS NULL AND expires_at > ?",
            Long.class, tokenHash, PURPOSE, now);
        return found.isEmpty() ? null : found.getFirst();
    }

    /**
     * 토큰을 쓰고(1회용) 비밀번호를 바꾼다 — 한 트랜잭션. 그 사이 다른 요청이 먼저 썼으면 null.
     *
     * @return 회원 번호
     */
    @Transactional
    Long useAndSetPassword(byte[] tokenHash, String passwordHash, LocalDateTime now) {
        List<Long> found = jdbc.queryForList(
            "SELECT user_id FROM auth_tokens WHERE token_hash = ? AND purpose = ? AND used_at IS NULL AND expires_at > ? FOR UPDATE",
            Long.class, tokenHash, PURPOSE, now);
        if (found.isEmpty()) {
            return null;
        }
        long userId = found.getFirst();
        jdbc.update("UPDATE auth_tokens SET used_at = ? WHERE user_id = ? AND purpose = ? AND used_at IS NULL", now, userId, PURPOSE);
        passwordCredentials.setEncoded(userId, passwordHash, now);
        return userId;
    }
}
