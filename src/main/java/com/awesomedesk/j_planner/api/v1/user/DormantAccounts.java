package com.awesomedesk.j_planner.api.v1.user;

import com.awesomedesk.j_planner.api.v1.me.AccountDeletionService;
import com.awesomedesk.j_planner.common.audit.AuditEvent;
import com.awesomedesk.j_planner.common.audit.AuditLogger;
import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import com.awesomedesk.j_planner.common.log.EmailMasker;
import com.awesomedesk.j_planner.common.mail.AuthMailer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 휴면 (US-38, D-061·D-069, 07 11-2). 기준 = 마지막 로그인(없으면 가입 시각).
 * <ol>
 *   <li>1년 되기 30일 전: 안내 메일 한 번 ({@code dormant_notice_sent_at})</li>
 *   <li>1년 지나고 안내 30일 뒤: 휴면 — 이메일·이름을 {@code dormant_profiles}로 옮기고 users 칸은 비움, 모든 기기 로그아웃, 남은 메일 링크 무효.
 *       비밀번호·구글 연결·작성 데이터는 그대로 (다시 로그인해 복원할 때 필요)</li>
 *   <li>휴면 4년 뒤: 탈퇴와 같은 순서로 모두 삭제</li>
 * </ol>
 * 정지(suspended_at)는 건드리지 않는다 — 정지된 회원도 휴면은 진행 (D-069). 복원은 비밀번호·연결된 구글로 로그인했을 때({@link #recordLogin}).
 * 대상을 고른 뒤 바꾸기·지우기 직전에 회원 행을 잠그고 조건을 다시 본다 — 로그인과 겹쳐도 로그인한 회원이 휴면·삭제되지 않게 (PO #7).
 */
@Slf4j
@Component
public class DormantAccounts {

    private static final String LOGIN_PATH = "/login";
    private static final Map<String, String> DORMANT_EXPIRED = Map.of("reason", "DORMANT_EXPIRED");
    private static final String BASE = "COALESCE(last_login_at, created_at)";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DeviceSessions deviceSessions;
    private final AccountDeletionService deletion;
    private final AuditLogger audit;
    private final AuthMailer mailer;
    private final Clock clock;
    private final String webBaseUrl;

    public DormantAccounts(JdbcTemplate jdbc, TransactionTemplate transaction, DeviceSessions deviceSessions, AccountDeletionService deletion,
                           AuditLogger audit, AuthMailer mailer, Clock clock, @Value("${app.web.base-url}") String webBaseUrl) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.deviceSessions = deviceSessions;
        this.deletion = deletion;
        this.audit = audit;
        this.mailer = mailer;
        this.clock = clock;
        this.webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
    }

    /** 매일 04:10 KST — 4년 지난 휴면 삭제 → 휴면 → 안내 순서 */
    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        int deleted = 0;
        for (long userId : dueForDeletion(now)) {
            deleted += deleteDormant(userId, now) ? 1 : 0;
        }
        int dormant = 0;
        for (long userId : dueForDormancy(now)) {
            dormant += toDormant(userId, now) ? 1 : 0;
        }
        int noticed = sendNotices(now);
        if (deleted + dormant + noticed > 0) {
            log.info("휴면 작업: 안내 {}명, 휴면 {}명, 삭제 {}명", noticed, dormant, deleted);
        }
    }

    /**
     * 안내 메일 — 보내기에 성공했을 때만 {@code dormant_notice_sent_at}을 남긴다. 실패하면 다음 날 다시 보낸다 (PO #7 1번).
     * 휴면은 안내를 남긴 날부터 30일 뒤라, 실패한 안내로 휴면이 되지는 않는다.
     */
    private int sendNotices(LocalDateTime now) {
        List<Map<String, Object>> due = jdbc.queryForList("SELECT user_id, email, name, " + BASE + " AS base FROM users"
            + " WHERE status = 'ACTIVE' AND dormant_notice_sent_at IS NULL AND " + BASE + " <= ?", now.minusYears(1).plusDays(30));
        int sent = 0;
        for (Map<String, Object> member : due) {
            long userId = ((Number) member.get("user_id")).longValue();
            String email = (String) member.get("email");
            LocalDateTime dormantAt = ((LocalDateTime) member.get("base")).plusYears(1);
            if (dormantAt.isBefore(now.plusDays(30))) {
                dormantAt = now.plusDays(30); // 안내가 늦었으면 보낸 지 30일 뒤에
            }
            try {
                mailer.sendDormantNotice(email, (String) member.get("name"), dormantAt.toLocalDate(), webBaseUrl + LOGIN_PATH);
            } catch (RuntimeException e) {
                log.error("휴면 안내 메일 실패 — 다음 작업 때 다시 보냄: {}", EmailMasker.mask(email), e);
                continue;
            }
            // 그 사이 로그인했으면(기준이 바뀜) 남기지 않는다
            sent += jdbc.update("UPDATE users SET dormant_notice_sent_at = ? WHERE user_id = ? AND status = 'ACTIVE' AND dormant_notice_sent_at IS NULL", now, userId);
        }
        return sent;
    }

    /** 휴면으로 바꿀 회원 (1년 지나고 안내 30일 지남). 바꿀 때 {@link #toDormant}가 조건을 다시 본다 */
    public List<Long> dueForDormancy(LocalDateTime now) {
        return jdbc.queryForList("SELECT user_id FROM users WHERE status = 'ACTIVE' AND " + BASE + " <= ? AND dormant_notice_sent_at <= ?",
            Long.class, now.minusYears(1), now.minusDays(30));
    }

    /**
     * 휴면으로 바꾼다. 회원 행을 잠그고 조건(ACTIVE·1년·안내 30일)을 다시 본 뒤에만 — 그 사이 로그인했으면 false (PO #7 2번).
     * 로그인도 같은 행을 잠그고 기록하므로({@link #recordLogin}) 둘이 겹치면 한쪽이 기다린다.
     */
    public boolean toDormant(long userId, LocalDateTime now) {
        Boolean moved = transaction.execute(status -> {
            List<Long> locked = jdbc.queryForList("SELECT user_id FROM users WHERE user_id = ? AND status = 'ACTIVE' AND " + BASE
                + " <= ? AND dormant_notice_sent_at <= ? FOR UPDATE", Long.class, userId, now.minusYears(1), now.minusDays(30));
            if (locked.isEmpty()) {
                return false;
            }
            jdbc.update("INSERT INTO dormant_profiles (user_id, email, name, moved_at) SELECT user_id, email, name, ? FROM users WHERE user_id = ?", now, userId);
            jdbc.update("UPDATE users SET email = NULL, name = NULL, status = 'DORMANT', dormant_at = ?, updated_at = ? WHERE user_id = ?", now, now, userId);
            jdbc.update("UPDATE auth_tokens SET used_at = ? WHERE user_id = ? AND used_at IS NULL", now, userId);
            return true;
        });
        if (!Boolean.TRUE.equals(moved)) {
            return false;
        }
        deviceSessions.endAll(userId);
        audit.system(userId, AuditEvent.DORMANT, Map.of());
        return true;
    }

    /** 삭제할 휴면 회원 (휴면 4년 지남). 지울 때 {@link #deleteDormant}가 조건을 다시 본다 */
    public List<Long> dueForDeletion(LocalDateTime now) {
        return jdbc.queryForList("SELECT user_id FROM users WHERE status = 'DORMANT' AND dormant_at <= ?", Long.class, now.minusYears(4));
    }

    /**
     * 휴면 4년 지난 회원을 모두 지운다. 회원 행을 잠그고 아직 DORMANT·4년인지 다시 본 뒤, 같은 트랜잭션에서 탈퇴와 같은 순서로 삭제 —
     * 그 사이 로그인해 복원됐으면 false (PO #7 3번). 감사 기록은 지우기로 정한 뒤에 남긴다.
     */
    public boolean deleteDormant(long userId, LocalDateTime now) {
        Boolean deleted = transaction.execute(status -> {
            List<Long> locked = jdbc.queryForList("SELECT user_id FROM users WHERE user_id = ? AND status = 'DORMANT' AND dormant_at <= ? FOR UPDATE",
                Long.class, userId, now.minusYears(4));
            if (locked.isEmpty()) {
                return false;
            }
            deletion.deleteRows(userId); // 같은 트랜잭션에 들어간다 (TransactionTemplate 기본 REQUIRED)
            return true;
        });
        if (!Boolean.TRUE.equals(deleted)) {
            return false;
        }
        audit.system(userId, AuditEvent.ACCOUNT_DELETED, DORMANT_EXPIRED);
        deviceSessions.endAll(userId);
        return true;
    }

    /**
     * 로그인 성공(비밀번호·연결된 구글 = 재인증)을 기록한다 — 세션을 만들기 <b>전에</b> 부른다. 정지 확인은 부르는 쪽이 먼저 한다 (정지 중이면 복원하지 않음).
     * 회원 행을 잠그고: 휴면이면 이메일·이름을 돌려놓고, 마지막 로그인 시각을 남기고 휴면 안내 기록을 지운다 (휴면 전환과 겹쳐도 한쪽이 기다림).
     *
     * @return 이번에 휴면에서 복원했으면 true
     */
    public boolean recordLogin(long userId, HttpServletRequest http) {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        MemberEmails.Contact restored = transaction.execute(status -> {
            jdbc.queryForList("SELECT user_id FROM users WHERE user_id = ? FOR UPDATE", Long.class, userId);
            MemberEmails.Contact profile = jdbc.query("SELECT email, name FROM dormant_profiles WHERE user_id = ?",
                rs -> rs.next() ? new MemberEmails.Contact(rs.getString("email"), rs.getString("name")) : null, userId);
            if (profile != null) {
                jdbc.update("DELETE FROM dormant_profiles WHERE user_id = ?", userId);
                jdbc.update("UPDATE users SET email = ?, name = ?, status = 'ACTIVE', dormant_at = NULL, updated_at = ? WHERE user_id = ?",
                    profile.email(), profile.name(), now, userId);
            }
            jdbc.update("UPDATE users SET last_login_at = ?, dormant_notice_sent_at = NULL WHERE user_id = ?", now, userId);
            return profile;
        });
        if (restored == null) {
            return false;
        }
        audit.user(userId, AuditEvent.DORMANT_RESTORED, http, Map.of());
        try {
            mailer.sendDormantRestored(restored.email(), restored.name());
        } catch (RuntimeException e) {
            log.error("휴면 복원 알림 메일 실패: {}", EmailMasker.mask(restored.email()), e);
        }
        return true;
    }
}
