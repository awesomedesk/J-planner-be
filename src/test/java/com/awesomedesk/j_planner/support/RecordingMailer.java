package com.awesomedesk.j_planner.support;

import com.awesomedesk.j_planner.common.mail.AuthMailer;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 테스트용 메일 발송: 보내지 않고 기억만 한다 (US-33·35·36·37·38). 테스트마다 비운다 (IntegrationTest) */
public class RecordingMailer implements AuthMailer {

    /** 보낸 메일 하나. link = 메일 안의 링크 */
    public record Sent(String type, String to, String name, String link) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failNext;
    private volatile Runnable duringNext;

    /** 다음 메일을 보내는 도중에 할 일 (예: 그 사이 로그인) — 경합 재현용 (US-38) */
    public void duringNext(Runnable action) {
        duringNext = action;
    }

    /** 다음 메일 하나는 보내기에 실패한다 (예외) — 메일 실패 처리·지표 확인용 (US-41) */
    public void failNext() {
        failNext = true;
    }

    private void record(Sent mail) {
        Runnable action = duringNext;
        if (action != null) {
            duringNext = null;
            action.run();
        }
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("가짜 메일 실패");
        }
        sent.add(mail);
    }

    @Override
    public void sendSignupVerification(String to, String name, String link) {
        record(new Sent("SIGNUP", to, name, link));
    }

    @Override
    public void sendPasswordReset(String to, String name, String link) {
        record(new Sent("RESET", to, name, link));
    }

    @Override
    public void sendPasswordChanged(String to, String name) {
        record(new Sent("PASSWORD_CHANGED", to, name, null));
    }

    @Override
    public void sendGoogleLinked(String to, String name) {
        record(new Sent("GOOGLE_LINKED", to, name, null));
    }

    @Override
    public void sendGoogleUnlinked(String to, String name) {
        record(new Sent("GOOGLE_UNLINKED", to, name, null));
    }

    @Override
    public void sendEmailChange(String to, String name, String link) {
        record(new Sent("EMAIL_CHANGE", to, name, link));
    }

    @Override
    public void sendEmailChangeRequested(String to, String name) {
        record(new Sent("EMAIL_CHANGE_NOTICE", to, name, null));
    }

    @Override
    public void sendAccountDeleted(String to, String name) {
        record(new Sent("ACCOUNT_DELETED", to, name, null));
    }

    @Override
    public void sendDormantNotice(String to, String name, LocalDate dormantOn, String link) {
        record(new Sent("DORMANT_NOTICE", to, name, link));
    }

    @Override
    public void sendDormantRestored(String to, String name) {
        record(new Sent("DORMANT_RESTORED", to, name, null));
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    /** 그 주소로 보낸 메일들 (보낸 순서) */
    public List<Sent> sentTo(String to) {
        return sent.stream().filter(m -> m.to().equals(to)).toList();
    }

    /** 그 주소로 보낸 그 종류(SIGNUP / RESET / PASSWORD_CHANGED / GOOGLE_LINKED / GOOGLE_UNLINKED / EMAIL_CHANGE / EMAIL_CHANGE_NOTICE / ACCOUNT_DELETED / DORMANT_NOTICE / DORMANT_RESTORED) 메일들 */
    public List<Sent> sentTo(String to, String type) {
        return sent.stream().filter(m -> m.to().equals(to) && m.type().equals(type)).toList();
    }

    public void clear() {
        sent.clear();
        failNext = false;
        duringNext = null;
    }
}
