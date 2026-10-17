package com.awesomedesk.j_planner.support;

import com.awesomedesk.j_planner.api.v1.auth.google.GoogleProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 메일·유출 비밀번호 확인·구글을 테스트용으로 바꾼다 (US-33·36). 밖으로 메일·요청을 보내지 않는다.
 * 구글은 127.0.0.1의 가짜 구글({@link FakeGoogle})을 보게 한다.
 */
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

    @Bean(destroyMethod = "close")
    public FakeGoogle fakeGoogle() {
        return new FakeGoogle();
    }

    @Bean
    @Primary
    public GoogleProperties fakeGoogleProperties(FakeGoogle google) {
        return new GoogleProperties(FakeGoogle.CLIENT_ID, FakeGoogle.CLIENT_SECRET,
            google.authorizationUri(), google.tokenUri(), google.jwkSetUri(), google.issuer());
    }
}
