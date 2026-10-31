package com.awesomedesk.j_planner.common.metrics;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 계정 메일 보내기마다 {@code jp_auth_mail_total{type, result}}를 센다 (US-41). 보내는 쪽 코드(try/catch)는 그대로 — 예외는 다시 던진다.
 * type: 가입 인증 SIGNUP, 비밀번호 재설정 RESET, 새 이메일 인증 EMAIL_CHANGE, 나머지 알림(비밀번호 바뀜·구글 연결·휴면 안내 …) NOTICE.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class MailMetricsAspect {

    private final SecurityMetrics metrics;

    @Around("execution(* com.awesomedesk.j_planner.common.mail.AuthMailer+.send*(..))")
    public Object count(ProceedingJoinPoint joinPoint) throws Throwable {
        String type = type(joinPoint.getSignature().getName());
        try {
            Object result = joinPoint.proceed();
            metrics.mail(type, "SENT");
            return result;
        } catch (Throwable e) {
            metrics.mail(type, "FAILED");
            throw e;
        }
    }

    static String type(String method) {
        return switch (method) {
            case "sendSignupVerification" -> "SIGNUP";
            case "sendPasswordReset" -> "RESET";
            case "sendEmailChange" -> "EMAIL_CHANGE";
            default -> "NOTICE";
        };
    }
}
