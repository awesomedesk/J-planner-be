package com.awesomedesk.j_planner.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 메일·유출 비밀번호 확인을 테스트용으로 바꾼다 (US-33). 밖으로 메일·요청을 보내지 않는다 */
@TestConfiguration
public class AuthTestConfig {

    @Bean
    @Primary
    public RecordingMailer recordingMailer() {
        return new RecordingMailer();
    }

    @Bean
    @Primary
    public FakeBreachedPasswords fakeBreachedPasswords() {
        return new FakeBreachedPasswords();
    }
}
