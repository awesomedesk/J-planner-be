package com.awesomedesk.j_planner.api.v1.auth.google;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 구글 연결 user_identities (07 11-2, V6) 읽기·쓰기. 구글 계정은 sub로만 찾는다.
 * (provider, provider_subject)·(user_id, provider) 유일 — 다른 회원의 구글·회원당 두 번째 구글은 DB도 막는다.
 */
@Component
@RequiredArgsConstructor
public class UserIdentities {

    static final String GOOGLE = "GOOGLE";

    private final JdbcTemplate jdbc;

    /** 연결된 구글 하나 */
    public record Identity(String provider, String email, LocalDateTime linkedAt) {
    }

    /** 이 구글 sub가 연결된 회원. 없으면 null */
    Long findUserBySubject(String sub) {
        return jdbc.query("SELECT user_id FROM user_identities WHERE provider = ? AND provider_subject = ?",
            rs -> rs.next() ? rs.getLong(1) : null, GOOGLE, sub);
    }

    /** 회원에 연결된 구글. 없으면 null */
    public Identity findGoogle(long userId) {
        List<Identity> found = jdbc.query("SELECT provider, email_at_link, linked_at FROM user_identities WHERE user_id = ? AND provider = ?",
            (rs, i) -> new Identity(rs.getString("provider"), rs.getString("email_at_link"), rs.getObject("linked_at", LocalDateTime.class)),
            userId, GOOGLE);
        return found.isEmpty() ? null : found.getFirst();
    }

    public boolean hasGoogle(long userId) {
        return findGoogle(userId) != null;
    }

    void insertGoogle(long userId, String sub, String email, LocalDateTime now) {
        jdbc.update("INSERT INTO user_identities (user_id, provider, provider_subject, email_at_link, linked_at) VALUES (?, ?, ?, ?, ?)",
            userId, GOOGLE, sub, email, now);
    }

    int deleteGoogle(long userId) {
        return jdbc.update("DELETE FROM user_identities WHERE user_id = ? AND provider = ?", userId, GOOGLE);
    }
}
