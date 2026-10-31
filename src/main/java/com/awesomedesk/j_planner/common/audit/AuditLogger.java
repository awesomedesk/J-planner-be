package com.awesomedesk.j_planner.common.audit;

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
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void user(Long userId, AuditEvent event, HttpServletRequest request, Map<String, String> detail) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent != null && userAgent.length() > USER_AGENT_MAX) {
            userAgent = userAgent.substring(0, USER_AGENT_MAX);
        }
        // TODO(US-41) 프록시 뒤 진짜 IP: 지금은 getRemoteAddr()라 Caddy/nginx 뒤에서는 모두 프록시 IP가 된다. tst·prd에 forward-headers + 믿는 프록시 주소만 설정 (09 US-41 AC)
        jdbc.update("INSERT INTO audit_logs (user_id, actor, event, ip, user_agent, detail, created_at) VALUES (?, 'USER', ?, ?, ?, ?, ?)",
            userId, event.name(), request.getRemoteAddr(), userAgent,
            detail == null || detail.isEmpty() ? null : jsonMapper.writeValueAsString(detail),
            LocalDateTime.now(clock).withNano(0));
    }

    /** 사람이 아닌 서버 작업이 한 일 (휴면·휴면 삭제, US-38). IP·브라우저 없음 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void system(long userId, AuditEvent event, Map<String, String> detail) {
        jdbc.update("INSERT INTO audit_logs (user_id, actor, event, detail, created_at) VALUES (?, 'SYSTEM', ?, ?, ?)",
            userId, event.name(), detail == null || detail.isEmpty() ? null : jsonMapper.writeValueAsString(detail),
            LocalDateTime.now(clock).withNano(0));
    }
}
