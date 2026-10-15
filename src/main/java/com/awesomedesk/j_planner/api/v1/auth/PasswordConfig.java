package com.awesomedesk.j_planner.api.v1.auth;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

@Configuration
class PasswordConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordVerifier.argon2();
    }

    /**
     * 유출 비밀번호 확인 (HIBP Pwned Passwords range API, 2초 안에 답이 없으면 막지 않고 진행).
     * {@code app.auth.hibp.enabled=false}면 확인하지 않는다 (인터넷이 없는 곳에서 개발할 때)
     */
    @Bean
    BreachedPasswords breachedPasswords(@Value("${app.auth.hibp.enabled:true}") boolean enabled,
                                        @Value("${app.auth.hibp.url:https://api.pwnedpasswords.com/range/}") String url) {
        if (!enabled) {
            return password -> false;
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(2));
        RestClient client = RestClient.builder().requestFactory(factory).build();
        return new HibpBreachedPasswords(prefix -> client.get()
            .uri(url + prefix)
            .header("Add-Padding", "true")
            .header("User-Agent", "j-planner")
            .retrieve()
            .body(String.class));
    }
}
