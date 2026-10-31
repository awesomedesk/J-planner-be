package com.awesomedesk.j_planner.common.audit;

import com.awesomedesk.j_planner.common.metrics.SecurityMetrics;
import com.awesomedesk.j_planner.common.net.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보안 감사 기록 audit_logs (07-db-design.md 11-2, 이벤트 목록 08 13-8).
 * 실패(401·403·429)도 남겨야 하므로 부른 쪽 트랜잭션과 따로 바로 저장한다.
 * detail에는 이메일·비밀번호·토큰·본문을 넣지 않는다 (부르는 쪽 책임, 키·값은 코드 값만).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogger {

    private static final int USER_AGENT_MAX = 255;

    private final JdbcTemplate jdbc;
    private final ClientIp clientIp;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final SecurityMetrics metrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void user(Long userId, AuditEvent event, HttpServletRequest request, Map<String, String> detail) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent != null && userAgent.length() > USER_AGENT_MAX) {
            userAgent = userAgent.substring(0, USER_AGENT_MAX);
        }
        jdbc.update("INSERT INTO audit_logs (user_id, actor, event, ip, user_agent, detail, created_at) VALUES (?, 'USER', ?, ?, ?, ?, ?)",
            userId, event.name(), clientIp.of(request), userAgent,
            detail == null || detail.isEmpty() ? null : jsonMapper.writeValueAsString(detail),
            LocalDateTime.now(clock).withNano(0));
        count(event, detail == null ? Map.of() : detail);
    }

    /**
     * 감사 기록과 같은 순간에 보안 지표도 센다 (US-41, 08 13-8): 로그인 성공·실패·정지, 요청 제한(rule).
     * 계정 대기(login-account)에 걸린 로그인 = LOCKED — 재인증(POST /me/reauth)의 대기도 같은 rule이라 함께 센다.
     */
    private void count(AuditEvent event, Map<String, String> detail) {
        String method = detail.getOrDefault("method", "PASSWORD");
        switch (event) {
            case LOGIN -> metrics.login("SUCCESS", method);
            case LOGIN_FAILED -> metrics.login("SUSPENDED".equals(detail.get("reason")) ? "SUSPENDED" : "FAILED", method);
            case RATE_LIMITED -> {
                String rule = detail.get("rule");
                if (rule != null) {
                    metrics.rateLimited(rule);
                }
                if ("login-account".equals(rule)) {
                    metrics.login("LOCKED", "PASSWORD");
                }
            }
            default -> {
            }
        }
    }

    /** 사람이 아닌 서버 작업이 한 일 (휴면·휴면 삭제, US-38). IP·브라우저 없음 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void system(long userId, AuditEvent event, Map<String, String> detail) {
        jdbc.update("INSERT INTO audit_logs (user_id, actor, event, detail, created_at) VALUES (?, 'SYSTEM', ?, ?, ?)",
            userId, event.name(), detail == null || detail.isEmpty() ? null : jsonMapper.writeValueAsString(detail),
            LocalDateTime.now(clock).withNano(0));
    }
}
