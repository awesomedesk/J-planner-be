package com.awesomedesk.j_planner.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 보안 지표 (US-41, 08 13-8 '보안 지표 이름'). Prometheus 이름은 끝에 {@code _total}이 붙는다 (예 {@code jp.auth.login} → {@code jp_auth_login_total}).
 * 어드민 알림 규칙이 이 이름·태그 값에 묶여 있다 — 바꾸지 않는다. <b>태그에 IP·이메일·회원 번호를 넣지 않는다</b>.
 * 로그인·요청 제한은 감사 기록과 같은 순간에 센다 ({@link com.awesomedesk.j_planner.common.audit.AuditLogger}).
 */
@Component
@RequiredArgsConstructor
public class SecurityMetrics {

    private final MeterRegistry registry;

    /** 태그 값마다 0으로 미리 만든다 — Prometheus increase()가 앱을 켠 뒤 첫 1건도 잡게 (ADMIN #300) */
    @PostConstruct
    void preRegister() {
        for (String result : new String[] {"SUCCESS", "FAILED", "LOCKED", "SUSPENDED"}) {
            for (String method : new String[] {"PASSWORD", "GOOGLE"}) {
                registry.counter("jp.auth.login", "result", result, "method", method);
            }
        }
        for (String type : new String[] {"SIGNUP", "RESET", "EMAIL_CHANGE", "NOTICE"}) {
            for (String result : new String[] {"SENT", "FAILED"}) {
                registry.counter("jp.auth.mail", "type", type, "result", result);
            }
        }
        registry.counter("jp.auth.breach_check_failed");
    }

    /**
     * @param result SUCCESS / FAILED / LOCKED / SUSPENDED
     * @param method PASSWORD / GOOGLE
     */
    public void login(String result, String method) {
        registry.counter("jp.auth.login", "result", result, "method", method).increment();
    }

    /**
     * @param type   SIGNUP / RESET / EMAIL_CHANGE / NOTICE
     * @param result SENT / FAILED
     */
    public void mail(String type, String result) {
        registry.counter("jp.auth.mail", "type", type, "result", result).increment();
    }

    /** @param rule 08 13-8 '요청 제한 rule 값' 표의 값 (감사 기록 detail.rule과 같게) */
    public void rateLimited(String rule) {
        registry.counter("jp.ratelimit.rejected", "rule", rule).increment();
    }

    /** 유출 비밀번호 확인(HIBP)에 닿지 못해 확인 없이 진행 (D-076) */
    public void breachCheckFailed() {
        registry.counter("jp.auth.breach_check_failed").increment();
    }
}
