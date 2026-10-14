package com.awesomedesk.j_planner.api.v1.auth;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 비밀번호 해시 password_credentials (07 11-2). 회원당 0~1행 — 구글로만 가입한 회원·admin 첫 설정 전에는 없다 */
@Component
@RequiredArgsConstructor
public class PasswordCredentials {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Clock clock;

    /** 비밀번호를 정하거나 바꾼다 (원문은 저장하지 않음) */
    public void set(long userId, String rawPassword) {
        jdbc.update("INSERT INTO password_credentials (user_id, password_hash, changed_at) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), changed_at = VALUES(changed_at)",
            userId, encoder.encode(rawPassword), LocalDateTime.now(clock).withNano(0));
    }

    /** 저장된 해시. 없으면 null */
    public String findHash(long userId) {
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM password_credentials WHERE user_id = ?", String.class, userId);
        return hashes.isEmpty() ? null : hashes.getFirst();
    }

    public boolean exists(long userId) {
        return findHash(userId) != null;
    }
}
