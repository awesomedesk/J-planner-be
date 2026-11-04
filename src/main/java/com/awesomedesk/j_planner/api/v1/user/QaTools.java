package com.awesomedesk.j_planner.api.v1.user;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/**
 * 로컬 실행 검수 도구 (PO #7 휴면, #354 재인증 만료). API·디버그 주소가 아니라 local 프로필에서 따로 한 번 실행하는 명령이다 ({@link QaToolsRunner}).
 * 대상은 {@code qa-…@example.com} 회원 한 명뿐이고, 결과는 개수만 돌려준다 (이메일·세션 ID·토큰·비밀번호 없음).
 * <ul>
 *   <li>{@code status}: 상태·휴면 여부·세션 수</li>
 *   <li>{@code dormant-notice}: 마지막 로그인을 '1년 되기 30일 전'으로 옮기고 그 회원에게만 휴면 안내 (매일 작업과 같은 코드)</li>
 *   <li>{@code make-dormant}: 마지막 로그인 1년+1일 전·안내 30일 전으로 옮기고 그 회원만 휴면 전환 (매일 작업과 같은 코드, 행 잠금·조건 재확인)</li>
 *   <li>{@code expire-reauth}: 그 회원의 세션이 정확히 하나일 때 그 세션의 재인증 시각만 지운다 (로그인은 그대로)</li>
 * </ul>
 */
public class QaTools {

    static final Pattern QA_EMAIL = Pattern.compile("qa-[a-z0-9-]+@example\\.com");
    /** {@link com.awesomedesk.j_planner.common.auth.Reauthentication}의 세션 속성 이름 */
    static final String REAUTH_AT = "jp.reauthAt";

    private final JdbcTemplate jdbc;
    private final DormantAccounts dormancy;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final Clock clock;

    public QaTools(JdbcTemplate jdbc, DormantAccounts dormancy, FindByIndexNameSessionRepository<? extends Session> sessions, Clock clock) {
        this.jdbc = jdbc;
        this.dormancy = dormancy;
        this.sessions = sessions;
        this.clock = clock;
    }

    /** @return 개수만 담은 한 줄 결과 */
    public String run(String email, String action) {
        if (email == null || !QA_EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("대상은 qa-…@example.com 회원만");
        }
        List<Long> ids = jdbc.queryForList("SELECT user_id FROM users WHERE email = ?", Long.class, email);
        if (ids.size() != 1) {
            throw new IllegalArgumentException("그 이메일의 (휴면 아닌) 회원이 정확히 1명이 아님: " + ids.size());
        }
        long userId = ids.getFirst();
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        return switch (action) {
            case "status" -> status(userId);
            case "dormant-notice" -> {
                jdbc.update("UPDATE users SET last_login_at = ?, dormant_notice_sent_at = NULL WHERE user_id = ? AND status = 'ACTIVE'",
                    now.minusYears(1).plusDays(29), userId);
                yield "notice=" + dormancy.sendNoticeTo(userId, now) + " " + status(userId);
            }
            case "make-dormant" -> {
                jdbc.update("UPDATE users SET last_login_at = ?, dormant_notice_sent_at = ? WHERE user_id = ? AND status = 'ACTIVE'",
                    now.minusYears(1).minusDays(1), now.minusDays(30), userId);
                yield "dormant=" + (dormancy.toDormant(userId, now) ? 1 : 0) + " " + status(userId);
            }
            case "expire-reauth" -> expireReauth(userId);
            default -> throw new IllegalArgumentException("모르는 action: " + action);
        };
    }

    private String expireReauth(long userId) {
        Map<String, ? extends Session> mine = sessions.findByPrincipalName(String.valueOf(userId));
        if (mine.size() != 1) {
            throw new IllegalStateException("세션이 정확히 1개가 아님: " + mine.size() + " — 바꾸지 않음");
        }
        Session session = mine.values().iterator().next();
        int expired = session.getAttribute(REAUTH_AT) != null ? 1 : 0;
        session.removeAttribute(REAUTH_AT);
        save(session);
        return "sessions=1 expired=" + expired;
    }

    @SuppressWarnings("unchecked")
    private <S extends Session> void save(Session session) {
        ((FindByIndexNameSessionRepository<S>) sessions).save((S) session);
    }

    private String status(long userId) {
        Map<String, Object> row = jdbc.queryForMap("SELECT status, dormant_notice_sent_at IS NOT NULL AS noticed FROM users WHERE user_id = ?", userId);
        int profile = jdbc.queryForObject("SELECT COUNT(*) FROM dormant_profiles WHERE user_id = ?", Integer.class, userId);
        return "status=" + row.get("status") + " noticed=" + row.get("noticed") + " dormantProfile=" + profile
            + " sessions=" + sessions.findByPrincipalName(String.valueOf(userId)).size();
    }
}
