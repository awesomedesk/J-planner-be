package com.awesomedesk.j_planner.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * 로그인 세션 = 기기 하나 (08-api-design.md 13-1).
 * <ul>
 *   <li>로그인할 때 이전 세션을 버리고 새 세션 ID 발급 (고정 공격 방지)</li>
 *   <li>계정당 기기 10대 — 넘으면 가장 오래 안 쓴 기기를 로그아웃</li>
 *   <li>모든 기기 로그아웃 = 회원별 세션 찾기(주체 이름 = user_id)로 지운다. 어드민도 같은 인덱스로 지운다 (07 11-6)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DeviceSessions {

    public static final int MAX_DEVICES = 10;
    /** 세션 속성: 로그인 시각(밀리초) — 최대 30일 기준 */
    static final String LOGIN_AT = "jp.loginAt";
    /** 세션 속성: 회원 상태를 마지막으로 확인한 시각(밀리초) — 1분마다 정지 확인 */
    static final String STATUS_CHECKED_AT = "jp.statusCheckedAt";
    /** 세션 속성: 기기 표시용 (US-37 기기 목록) */
    static final String USER_AGENT = "jp.userAgent";
    static final String IP = "jp.ip";

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final SecurityContextRepository securityContextRepository;
    private final Clock clock;

    /** 로그인: 새 세션을 만들고 회원을 넣는다 */
    public void start(long userId, HttpServletRequest request, HttpServletResponse response) {
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AuthUser(userId).toAuthentication());
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        long now = clock.millis();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, String.valueOf(userId));
        session.setAttribute(LOGIN_AT, now);
        session.setAttribute(STATUS_CHECKED_AT, now);
        session.setAttribute(USER_AGENT, request.getHeader("User-Agent"));
        session.setAttribute(IP, request.getRemoteAddr());
        keepNewest(userId, session.getId());
    }

    /** 이 기기 로그아웃 */
    public void end(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /** 이 기기 빼고 모두 로그아웃. 지운 수 */
    public int endOthers(long userId, String currentSessionId) {
        int count = 0;
        for (String id : sessions.findByPrincipalName(String.valueOf(userId)).keySet()) {
            if (!id.equals(currentSessionId)) {
                sessions.deleteById(id);
                count++;
            }
        }
        return count;
    }

    /** 새 세션 말고 이미 10대면, 가장 오래 안 쓴 것부터 지워 새 세션까지 10대로 맞춘다 */
    private void keepNewest(long userId, String newSessionId) {
        Map<String, ? extends Session> mine = sessions.findByPrincipalName(String.valueOf(userId));
        List<? extends Session> others = mine.values().stream()
            .filter(s -> !s.getId().equals(newSessionId))
            .sorted(Comparator.comparing(Session::getLastAccessedTime))
            .toList();
        int over = others.size() - (MAX_DEVICES - 1);
        for (int i = 0; i < over; i++) {
            sessions.deleteById(others.get(i).getId());
        }
    }
}
