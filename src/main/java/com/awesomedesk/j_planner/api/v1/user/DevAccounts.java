package com.awesomedesk.j_planner.api.v1.user;

import com.awesomedesk.j_planner.api.v1.auth.PasswordCredentials;
import com.awesomedesk.j_planner.common.log.EmailMasker;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발 전용: 로그인할 계정을 준비한다 (US-34. 가입(US-33)·비밀번호 찾기(US-35) 전이라).
 * <b>local 프로필에서만</b>. 설정 {@code app.auth.dev-accounts} = 환경 변수 {@code JP_DEV_ACCOUNTS}(.env)
 * — {@code 이메일:비밀번호} 를 {@code ;}로 여러 개. 예: {@code JP_DEV_ACCOUNTS=me@example.com:비밀번호15자이상;qa@example.com:비밀번호15자이상}
 * <ul>
 *   <li>회원이 없으면 만든다 (이름 = 이메일 @ 앞, 기본 데이터 포함) — 검수용 두 번째 계정</li>
 *   <li>비밀번호가 아직 없을 때만 정한다 (admin 첫 비밀번호). 이미 있으면 그대로</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DevAccounts implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final UserDataInitializer userDataInitializer;
    private final PasswordCredentials passwordCredentials;

    @Value("${app.auth.dev-accounts:}")
    private String accounts;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String entry : accounts.split(";")) {
            int colon = entry.indexOf(':');
            if (entry.isBlank() || colon <= 0) {
                continue;
            }
            String email = entry.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String password = entry.substring(colon + 1);
            List<Long> ids = jdbc.queryForList("SELECT user_id FROM users WHERE email = ?", Long.class, email);
            long userId;
            if (ids.isEmpty()) {
                jdbc.update("INSERT INTO users (email, name, status) VALUES (?, ?, 'ACTIVE')", email, email.substring(0, email.indexOf('@') < 0 ? email.length() : email.indexOf('@')));
                userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
                userDataInitializer.createDefaults(userId);
                log.info("로컬 계정 만듦: {}", EmailMasker.mask(email));
            } else {
                userId = ids.getFirst();
            }
            if (!passwordCredentials.exists(userId)) {
                passwordCredentials.set(userId, password);
                log.info("로컬 계정 비밀번호 정함: {}", EmailMasker.mask(email));
            }
        }
    }
}
