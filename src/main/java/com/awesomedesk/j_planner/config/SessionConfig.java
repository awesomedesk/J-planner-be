package com.awesomedesk.j_planner.config;

import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import com.awesomedesk.j_planner.common.auth.SessionPolicyInterceptor;
import java.time.Clock;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 로그인 유지 = 서버 세션 (Spring Session + Redis, 08-api-design.md 13-1, D-061).
 * <ul>
 *   <li>키: {@code jp:session:*} (회원별 세션 찾기 인덱스 포함 — 모든 기기 로그아웃·어드민 세션 삭제, 07 11-6)</li>
 *   <li>14일 안 쓰면 만료, 로그인부터 최대 30일(연장 없음, {@code SessionPolicyInterceptor}), 기기 10대({@code DeviceSessions})</li>
 *   <li>쿠키 {@code __Host-JP_SID}: Secure · HttpOnly · SameSite=Lax · Path=/ · Domain 없음 · 30일 (브라우저를 껐다 켜도 유지)</li>
 *   <li>Redis CONFIG 명령은 쓰지 않는다(운영 Redis는 막음). 만료 알림이 필요하면 redis.conf에 {@code notify-keyspace-events Egx}</li>
 * </ul>
 */
@Configuration
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = SessionConfig.IDLE_SECONDS, redisNamespace = SessionConfig.NAMESPACE)
public class SessionConfig {

    public static final String NAMESPACE = "jp:session";
    public static final String COOKIE = "__Host-JP_SID";
    public static final int IDLE_SECONDS = 14 * 24 * 60 * 60;
    public static final Duration MAX_AGE = Duration.ofDays(30);

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(COOKIE);
        serializer.setCookiePath("/");
        serializer.setUseSecureCookie(true);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookieMaxAge((int) MAX_AGE.toSeconds());
        return serializer;
    }

    @Bean
    public SessionPolicyInterceptor sessionPolicyInterceptor(JdbcTemplate jdbc, DeviceSessions deviceSessions, Clock clock) {
        return new SessionPolicyInterceptor(jdbc, deviceSessions, clock);
    }

    @Bean
    public static ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }
}
