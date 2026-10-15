package com.awesomedesk.j_planner.common.mail;

/**
 * 계정 메일 보내기 (US-33 가입 인증. 비밀번호 재설정·알림은 US-35·37에서 더한다).
 * 보내는 주소는 설정 {@code app.mail.from} (D-074: no-reply@awesomedesk.co.kr 형식).
 * 지금은 로컬·테스트 서버용 {@link LoggingAuthMailer}(실제로 보내지 않음)뿐. 실제 발송(Amazon SES 서울 예정)은 도메인 구입·배포 준비 때.
 */
public interface AuthMailer {

    /**
     * 가입 인증 메일
     *
     * @param to   받는 주소 (소문자)
     * @param name 가입할 때 쓴 이름
     * @param link 인증 링크 (FE 화면 주소 + 토큰). 1회용·24시간
     */
    void sendSignupVerification(String to, String name, String link);
}
