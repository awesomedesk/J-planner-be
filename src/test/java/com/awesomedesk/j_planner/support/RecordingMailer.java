package com.awesomedesk.j_planner.support;

import com.awesomedesk.j_planner.common.mail.AuthMailer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 테스트용 메일 발송: 보내지 않고 기억만 한다 (US-33). 테스트마다 비운다 (IntegrationTest) */
public class RecordingMailer implements AuthMailer {

    /** 보낸 메일 하나. link = 메일 안의 링크 */
    public record Sent(String type, String to, String name, String link) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendSignupVerification(String to, String name, String link) {
        sent.add(new Sent("SIGNUP", to, name, link));
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    /** 그 주소로 보낸 메일들 (보낸 순서) */
    public List<Sent> sentTo(String to) {
        return sent.stream().filter(m -> m.to().equals(to)).toList();
    }

    public void clear() {
        sent.clear();
    }
}
