package com.awesomedesk.j_planner.common.mail;

import com.awesomedesk.j_planner.common.log.EmailMasker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 가짜 메일 발송: 실제로 보내지 않고 로그에 남긴다 (D-074: 도메인 구입 전까지 로컬은 가짜 발송).
 * 로컬에서 가입을 시험할 때 로그의 링크를 브라우저에 붙여 넣는다.
 * <b>운영(prd)에서는 만들지 않는다</b> — 링크(토큰)가 로그에 남으면 안 되므로, 실제 발송 구현이 없으면 운영 서버가 켜지지 않게 한다.
 */
@Slf4j
@Component
@Profile("!prd")
public class LoggingAuthMailer implements AuthMailer {

    private final String from;

    public LoggingAuthMailer(@Value("${app.mail.from}") String from) {
        this.from = from;
    }

    @Override
    public void sendSignupVerification(String to, String name, String link) {
        log.info("[가짜 메일] 가입 인증 from={} to={} — 링크: {}", from, EmailMasker.mask(to), link);
    }
}
