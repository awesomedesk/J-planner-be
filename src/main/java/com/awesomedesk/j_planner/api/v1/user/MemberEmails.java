package com.awesomedesk.j_planner.api.v1.user;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 이메일로 회원 찾기 — 휴면 회원(US-38)의 이메일은 {@code users}가 아니라 {@code dormant_profiles}에 있다.
 * 가입·이메일 변경·구글 가입의 '이미 가입됨'과 로그인·비밀번호 찾기는 두 테이블을 모두 본다 (07 11-1 '같은 이메일로 두 번째 계정 없음').
 */
@Component
@RequiredArgsConstructor
public class MemberEmails {

    private final JdbcTemplate jdbc;

    /** 그 이메일의 회원 번호 (휴면 회원 포함). 없으면 null */
    public Long findUserId(String email) {
        return jdbc.query("SELECT user_id FROM users WHERE email = ? UNION ALL SELECT user_id FROM dormant_profiles WHERE email = ?",
            rs -> rs.next() ? rs.getLong(1) : null, email, email);
    }

    public boolean taken(String email) {
        return findUserId(email) != null;
    }

    /** 회원의 이메일·이름 (휴면이면 dormant_profiles의 값). 회원이 없으면 null */
    public Contact contact(long userId) {
        return jdbc.query("""
                SELECT COALESCE(u.email, d.email) AS email, COALESCE(u.name, d.name) AS name
                FROM users u LEFT JOIN dormant_profiles d ON d.user_id = u.user_id WHERE u.user_id = ?""",
            rs -> rs.next() ? new Contact(rs.getString("email"), rs.getString("name")) : null, userId);
    }

    public record Contact(String email, String name) {
    }
}
