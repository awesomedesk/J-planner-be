package com.awesomedesk.j_planner.api.v1.user;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * {@link QaTools}를 한 번 실행하고 끝나는 명령 — local 프로필 + {@code app.qa.action}이 있을 때만 생긴다 (운영·테스트 서버에는 없음).
 * <pre>
 * ./gradlew bootRun --args='--server.port=0 --management.server.port=-1 --app.qa.email=qa-…@example.com --app.qa.action=status'
 * </pre>
 * 아무 빈 포트로 몇 초 떠서(8080·8081과 겹치지 않음, 관리 포트 끔) 결과 한 줄(개수만)을 남기고 바로 종료한다. 실패하면 종료 코드 1.
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.qa.action")
@RequiredArgsConstructor
class QaToolsRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final DormantAccounts dormancy;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final Clock clock;
    private final ConfigurableApplicationContext context;

    @Value("${app.qa.email:}")
    private String email;

    @Value("${app.qa.action}")
    private String action;

    @Override
    public void run(ApplicationArguments args) {
        int code = 0;
        try {
            log.warn("[QA 도구] {} → {}", action, new QaTools(jdbc, dormancy, sessions, clock).run(email, action));
        } catch (RuntimeException e) {
            log.error("[QA 도구] 멈춤 — 바꾼 것 없음: {}", e.getMessage());
            code = 1;
        }
        int exit = code;
        System.exit(SpringApplication.exit(context, () -> exit));
    }
}
