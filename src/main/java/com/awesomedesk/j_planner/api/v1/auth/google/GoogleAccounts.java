package com.awesomedesk.j_planner.api.v1.auth.google;

import com.awesomedesk.j_planner.api.v1.auth.EmailAddress;
import com.awesomedesk.j_planner.api.v1.auth.PasswordCredentials;
import com.awesomedesk.j_planner.api.v1.user.DormantAccounts;
import com.awesomedesk.j_planner.api.v1.user.MemberEmails;
import com.awesomedesk.j_planner.api.v1.user.UserDataInitializer;
import com.awesomedesk.j_planner.common.audit.AuditEvent;
import com.awesomedesk.j_planner.common.audit.AuditLogger;
import com.awesomedesk.j_planner.common.auth.Reauthentication;
import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.common.log.EmailMasker;
import com.awesomedesk.j_planner.common.mail.AuthMailer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구글 계정 규칙 (US-36, 08 13-5, D-061: 합치기 없음, 연결은 내 계정에서만).
 * <table>
 *   <tr><td>연결된 구글(sub)</td><td>그 회원으로 로그인 (정지면 ACCOUNT_SUSPENDED, 휴면이면 복원 — US-38)</td></tr>
 *   <tr><td>처음 보는 구글 + 같은 이메일 회원 없음 + email_verified</td><td>가입 확인 화면 /signup/google (D-083) → [가입하기]에서
 *       새 회원 + 연결 + 기본 데이터, 같은 이메일 임시 가입은 지움. 다시 로그인 팝업이면 가입하지 않고 GOOGLE_NOT_REGISTERED</td></tr>
 *   <tr><td>처음 보는 구글 + 같은 이메일 회원 있음 (휴면 회원 포함)</td><td>새 계정 안 만듦 → GOOGLE_EMAIL_ACCOUNT_EXISTS</td></tr>
 *   <tr><td>email_verified 아님·이메일 없음</td><td>GOOGLE_FAILED</td></tr>
 * </table>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAccounts {

    static final int NAME_MAX = 50;
    private static final Map<String, String> GOOGLE_METHOD = Map.of("method", "GOOGLE");
    private static final Map<String, String> GOOGLE_PROVIDER = Map.of("provider", "GOOGLE");

    private final JdbcTemplate jdbc;
    private final UserIdentities identities;
    private final PasswordCredentials passwordCredentials;
    private final UserDataInitializer userDataInitializer;
    private final Reauthentication reauthentication;
    private final AuditLogger audit;
    private final AuthMailer mailer;
    private final TransactionTemplate transaction;
    private final com.awesomedesk.j_planner.api.v1.auth.LoginMethods loginMethods;
    private final MemberEmails memberEmails;
    private final DormantAccounts dormantAccounts;
    private final Clock clock;

    /** 구글 로그인 결과: 로그인할 회원, 가입 확인이 필요함(D-083), 또는 FE에 보낼 오류 코드 */
    record LoginResult(Long userId, boolean signupNeeded, String error) {

        static LoginResult ok(long userId) {
            return new LoginResult(userId, false, null);
        }

        static LoginResult needsSignup() {
            return new LoginResult(null, true, null);
        }

        static LoginResult fail(String error) {
            return new LoginResult(null, false, error);
        }
    }

    /**
     * 구글에서 돌아온 사람: 연결된 회원이면 로그인할 회원, 처음 보는 구글이면 가입 확인 화면이 필요한지(D-083) 또는 오류.
     * 회원은 여기서 만들지 않는다 — 확인 화면의 [가입하기]({@link GoogleSignupService})에서 만든다.
     */
    LoginResult login(GoogleProfile google, HttpServletRequest http) {
        Long linked = identities.findUserBySubject(google.sub());
        if (linked != null) {
            if (suspended(linked)) { // D-069
                audit.user(linked, AuditEvent.LOGIN_FAILED, http, Map.of("method", "GOOGLE", "reason", "SUSPENDED"));
                return LoginResult.fail(GoogleFlow.ACCOUNT_SUSPENDED);
            }
            return LoginResult.ok(linked);
        }
        if (!google.emailVerified() || google.email() == null || !EmailAddress.isValid(google.email())) {
            log.info("구글 가입 거절: 이메일 인증 안 됨 또는 이메일 없음");
            return LoginResult.fail(GoogleFlow.GOOGLE_FAILED);
        }
        if (memberEmails.taken(google.email())) {
            log.info("구글 가입 거절 — 같은 이메일 회원 있음: {}", EmailMasker.mask(google.email()));
            return LoginResult.fail(GoogleFlow.GOOGLE_EMAIL_ACCOUNT_EXISTS);
        }
        return LoginResult.needsSignup();
    }

    /**
     * 확인 화면의 [가입하기] (D-083): 새 회원 + 구글 연결 + 기본 데이터, 같은 이메일 임시 가입 삭제 (한 트랜잭션) + 감사 기록.
     *
     * @return 새 회원 번호. 같은 구글이 그 사이 가입했으면 null
     * @throws com.awesomedesk.j_planner.common.error.EmailAlreadyRegisteredException 그 사이 같은 이메일 회원이 생김
     */
    Long completeSignup(GoogleProfile google, String name, HttpServletRequest http) {
        Long userId;
        try {
            userId = transaction.execute(status -> {
                if (identities.findUserBySubject(google.sub()) != null) {
                    return null;
                }
                Long sameEmail = memberEmails.findUserId(google.email());
                if (sameEmail != null) {
                    throw new com.awesomedesk.j_planner.common.error.EmailAlreadyRegisteredException(loginMethods.forConflict(sameEmail));
                }
                return signup(google, name);
            });
        } catch (DuplicateKeyException race) { // 같은 구글·이메일로 동시에 가입
            log.info("구글 가입 겹침: {}", EmailMasker.mask(google.email()));
            return null;
        }
        if (userId != null) {
            audit.user(userId, AuditEvent.SIGNUP_COMPLETED, http, GOOGLE_METHOD);
        }
        return userId;
    }

    /**
     * 로그인 기록 — 세션을 만들기 전에: 휴면이면 복원(US-38, 정지는 login에서 걸렀다) + 마지막 로그인 시각·휴면 안내 기록 지움 + 감사 기록.
     *
     * @return 결과 — {@code exists=false}(그 사이 회원이 지워짐)면 세션을 만들지 않는다
     */
    DormantAccounts.LoginRecord recordLogin(long userId, HttpServletRequest http) {
        DormantAccounts.LoginRecord record = dormantAccounts.recordLogin(userId, http);
        if (record.exists()) {
            audit.user(userId, AuditEvent.LOGIN, http, GOOGLE_METHOD);
        }
        return record;
    }

    /**
     * 내 계정에 구글 연결 (재인증은 시작할 때 확인했다).
     *
     * @return null = 연결됨, 아니면 FE에 보낼 오류 코드
     */
    String connect(long userId, GoogleProfile google, HttpServletRequest http) {
        Long owner = identities.findUserBySubject(google.sub());
        if (owner != null) {
            return owner == userId ? null : GoogleFlow.GOOGLE_ALREADY_LINKED;
        }
        if (identities.hasGoogle(userId)) { // 시작한 뒤 다른 구글이 먼저 연결됨
            return GoogleFlow.GOOGLE_FAILED;
        }
        try {
            identities.insertGoogle(userId, google.sub(), google.email(), now());
        } catch (DuplicateKeyException race) {
            return GoogleFlow.GOOGLE_ALREADY_LINKED;
        }
        audit.user(userId, AuditEvent.IDENTITY_LINKED, http, GOOGLE_PROVIDER);
        Member member = member(userId);
        sendQuietly(() -> mailer.sendGoogleLinked(member.email(), member.name()), member.email());
        return null;
    }

    /** 연결 시작 전 확인: 이미 구글이 있으면 409, 5분 안에 재인증 안 했으면 403 */
    public void checkConnect(long userId, HttpServletRequest http) {
        if (identities.hasGoogle(userId)) {
            throw new ApiException(ErrorCode.IDENTITY_ALREADY_CONNECTED);
        }
        reauthentication.require(http);
    }

    /** 구글 재인증 시작 전 확인: 연결된 구글이 없으면 404 (비밀번호 재인증을 쓰면 된다) */
    public void checkReauth(long userId) {
        if (!identities.hasGoogle(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    /**
     * 구글 재인증 콜백 (US-37): 이 회원에 연결된 구글(sub)이고, 방금(5분 안) 구글에서 다시 로그인했으면 이 기기를 재인증.
     * 구글이 준 auth_time은 구글 시계라 서버의 실제 시각과 비교한다.
     *
     * @return null = 재인증됨, 아니면 FE에 보낼 오류 코드
     */
    String reauth(long userId, GoogleProfile google, java.time.Instant authTime, HttpServletRequest http) {
        Long owner = identities.findUserBySubject(google.sub());
        if (owner == null || owner != userId) {
            return GoogleFlow.GOOGLE_ACCOUNT_MISMATCH;
        }
        java.time.Instant now = java.time.Instant.now();
        if (authTime == null || authTime.isBefore(now.minus(Reauthentication.VALID_FOR)) || authTime.isAfter(now.plusSeconds(60))) {
            log.info("구글 재인증 거절: 구글에서 다시 로그인하지 않음(auth_time)");
            return GoogleFlow.GOOGLE_FAILED;
        }
        reauthentication.mark(http);
        return null;
    }

    /** 내 계정의 연결 목록 (지금은 구글 0~1개) */
    public List<UserIdentities.Identity> list(long userId) {
        UserIdentities.Identity google = identities.findGoogle(userId);
        return google == null ? List.of() : List.of(google);
    }

    /** 구글 연결 해제: 연결 없으면 404, 비밀번호가 없으면(마지막 로그인 수단) 409, 재인증 5분 지나면 403 */
    public void disconnect(long userId, HttpServletRequest http) {
        if (!identities.hasGoogle(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (!passwordCredentials.exists(userId)) {
            throw new ApiException(ErrorCode.LAST_IDENTITY);
        }
        reauthentication.require(http);
        identities.deleteGoogle(userId);
        audit.user(userId, AuditEvent.IDENTITY_UNLINKED, http, GOOGLE_PROVIDER);
        Member member = member(userId);
        sendQuietly(() -> mailer.sendGoogleUnlinked(member.email(), member.name()), member.email());
    }

    /** 새 회원 + 구글 연결 + 기본 데이터 (트랜잭션 안에서 부른다) */
    private long signup(GoogleProfile google, String name) {
        String email = google.email();
        jdbc.update("DELETE FROM pending_signups WHERE email = ?", email); // D-061: 임시 가입 중이면 지우고 구글로 가입 (이전 링크 무효)
        LocalDateTime now = now();
        jdbc.update("INSERT INTO users (email, name, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', ?, ?)", email, name, now, now);
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
        userDataInitializer.createDefaults(userId);
        identities.insertGoogle(userId, google.sub(), email, now);
        return userId;
    }

    /** 확인 화면에 채워 둘 이름 (D-082 1번·D-083) = 구글 이름(앞뒤 공백 정리). 없으면 이메일 @ 앞부분. 50자(코드 포인트)까지 */
    static String displayName(GoogleProfile google) {
        String name = google.name() == null ? "" : google.name().strip();
        if (name.isEmpty()) {
            name = google.email().substring(0, google.email().indexOf('@'));
        }
        if (name.codePointCount(0, name.length()) > NAME_MAX) {
            name = name.substring(0, name.offsetByCodePoints(0, NAME_MAX));
        }
        return name;
    }

    private boolean suspended(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_id = ? AND suspended_at IS NOT NULL", Integer.class, userId) > 0;
    }

    private Member member(long userId) {
        return jdbc.queryForObject("SELECT email, name FROM users WHERE user_id = ?",
            (rs, i) -> new Member(rs.getString("email"), rs.getString("name")), userId);
    }

    private static void sendQuietly(Runnable send, String email) {
        try {
            send.run();
        } catch (RuntimeException e) {
            // 알림 메일이 안 가도 연결·해제는 그대로.
            log.error("구글 연결 알림 메일 실패: {}", EmailMasker.mask(email), e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private record Member(String email, String name) {
    }
}
