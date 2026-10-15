package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.api.v1.user.UserDataInitializer;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 임시 가입 pending_signups (07 11-2, D-061) 읽기·쓰기 */
@Component
@RequiredArgsConstructor
class PendingSignups {

    private final JdbcTemplate jdbc;
    private final PasswordCredentials passwordCredentials;
    private final UserDataInitializer userDataInitializer;

    /** 같은 이메일이 있으면 새 값으로 바꾼다 (이전 링크 무효) */
    void save(String email, String name, String passwordHash, byte[] tokenHash, LocalDateTime now, LocalDateTime expiresAt) {
        jdbc.update("INSERT INTO pending_signups (email, name, password_hash, token_hash, expires_at, last_sent_at, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), password_hash = VALUES(password_hash), "
                + "token_hash = VALUES(token_hash), expires_at = VALUES(expires_at), last_sent_at = VALUES(last_sent_at), created_at = VALUES(created_at)",
            email, name, passwordHash, tokenHash, expiresAt, now, now);
    }

    /** 아직 유효한 임시 가입 (다시 보내기용). 없으면 null */
    Pending findActiveByEmail(String email, LocalDateTime now) {
        List<Pending> found = jdbc.query("SELECT pending_id, name, last_sent_at FROM pending_signups WHERE email = ? AND expires_at > ?",
            (rs, i) -> new Pending(rs.getLong("pending_id"), rs.getString("name"), rs.getObject("last_sent_at", LocalDateTime.class)),
            email, now);
        return found.isEmpty() ? null : found.getFirst();
    }

    /** 새 링크로 바꾸고 보낸 시각을 남긴다 (이전 링크 무효) */
    void renewToken(long pendingId, byte[] tokenHash, LocalDateTime now) {
        jdbc.update("UPDATE pending_signups SET token_hash = ?, last_sent_at = ? WHERE pending_id = ?", tokenHash, now, pendingId);
    }

    /**
     * 인증: 유효한 토큰이면 한 트랜잭션에서 users + password_credentials + 기본 데이터(11-4)를 만들고 임시 가입을 지운다.
     * 그 사이 같은 이메일 회원이 생겼으면(예: 구글 가입) 임시 가입만 지운다.
     *
     * @return 새 회원 번호. 토큰이 없거나 만료됐거나 이미 회원이면 null
     */
    @Transactional
    Long verify(byte[] tokenHash, LocalDateTime now) {
        List<Row> found = jdbc.query(
            "SELECT pending_id, email, name, password_hash FROM pending_signups WHERE token_hash = ? AND expires_at > ? FOR UPDATE",
            (rs, i) -> new Row(rs.getLong("pending_id"), rs.getString("email"), rs.getString("name"), rs.getString("password_hash")),
            tokenHash, now);
        if (found.isEmpty()) {
            return null;
        }
        Row pending = found.getFirst();
        jdbc.update("DELETE FROM pending_signups WHERE pending_id = ?", pending.id());
        Long existing = jdbc.query("SELECT user_id FROM users WHERE email = ?", rs -> rs.next() ? rs.getLong(1) : null, pending.email());
        if (existing != null) {
            return null;
        }
        jdbc.update("INSERT INTO users (email, name, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', ?, ?)",
            pending.email(), pending.name(), now, now);
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, pending.email());
        passwordCredentials.setHash(userId, pending.passwordHash());
        userDataInitializer.createDefaults(userId);
        return userId;
    }

    /** 만료된 임시 가입을 지운다 */
    int deleteExpired(LocalDateTime now) {
        return jdbc.update("DELETE FROM pending_signups WHERE expires_at <= ?", now);
    }

    record Pending(long id, String name, LocalDateTime lastSentAt) {
    }

    private record Row(long id, String email, String name, String passwordHash) {
    }
}
