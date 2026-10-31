package com.awesomedesk.j_planner.common.mail;

import java.time.LocalDate;

/**
 * 계정 메일 보내기 (US-33 가입 인증, US-35 비밀번호 재설정·변경 알림, US-36 구글 연결·해제 알림, US-37 이메일 변경·탈퇴, US-38 휴면 안내·복원).
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

    /**
     * 비밀번호 재설정 메일 (US-35)
     *
     * @param link 재설정 링크 (FE 화면 주소 + 토큰). 1회용·30분
     */
    void sendPasswordReset(String to, String name, String link);

    /** 비밀번호가 바뀌었다는 알림 (US-35 재설정, US-37 변경). 링크 없음 — '본인이 아니면 비밀번호 찾기로 바꾸세요' */
    void sendPasswordChanged(String to, String name);

    /** 내 계정에 구글이 연결됐다는 알림 (US-36). 링크 없음 — '본인이 아니면 비밀번호를 바꾸고 연결을 해제하세요' */
    void sendGoogleLinked(String to, String name);

    /** 구글 연결이 해제됐다는 알림 (US-36). 링크 없음 */
    void sendGoogleUnlinked(String to, String name);

    /**
     * 이메일 변경 — 새 주소로 보내는 인증 메일 (US-37)
     *
     * @param link 인증 링크 (FE 화면 주소 + 토큰). 1회용·24시간
     */
    void sendEmailChange(String to, String name, String link);

    /** 이메일 변경 요청이 있었다는 알림 — 기존 주소로 (US-37, D-066). 링크 없음 — '본인이 아니면 비밀번호를 바꾸세요' */
    void sendEmailChangeRequested(String to, String name);

    /** 탈퇴가 끝났다는 알림 (US-37). 회원 정보를 지운 뒤 보낸다 */
    void sendAccountDeleted(String to, String name);

    /**
     * 휴면 30일 전 안내 (US-38)
     *
     * @param dormantOn 이날까지 로그인하지 않으면 휴면
     * @param link      로그인 화면 주소
     */
    void sendDormantNotice(String to, String name, LocalDate dormantOn, String link);

    /** 휴면에서 복원됐다는 알림 (US-38). 링크 없음 — '본인이 아니면 비밀번호를 바꾸세요' */
    void sendDormantRestored(String to, String name);
}
