package com.awesomedesk.j_planner.support;

import com.awesomedesk.j_planner.api.v1.auth.PasswordCredentials;
import com.awesomedesk.j_planner.api.v1.user.UserDataInitializer;
import com.awesomedesk.j_planner.common.auth.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 실제 MySQL(테스트 DB jp_test)로 API 전체를 확인하는 테스트의 부모.
 * 테이블은 Flyway 마이그레이션으로 만들고(TestFlywayConfig), 테스트마다 sql/reset.sql로 데이터를 비운다.
 * 모든 응답은 명세(08-openapi.yaml)와 자동 대조한다 (OpenApiContract).
 * "지금"은 FixedClockConfig의 2026-09-25 09:00 (한국 시간)으로 고정한다.
 * 요청은 기본으로 admin 계정(user_id = 1)이 로그인한 것으로 보낸다 (LoginUserConfig, US-32).
 * Redis(세션·요청 제한, US-34)도 테스트마다 비운다 (1번 DB만 — 로컬 서버가 쓰는 0번은 그대로). 로컬 Redis 127.0.0.1:6379가 있어야 한다.
 * 로컬 MySQL에 jp_test를 만들 수 있는 계정이 있어야 한다 (JP_TEST_DB_URL / USERNAME / PASSWORD로 바꿀 수 있음).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({FixedClockConfig.class, TestFlywayConfig.class, OpenApiContract.class, LoginUserConfig.class})
@Sql(scripts = "classpath:sql/reset.sql")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TestClock clock;

    @Autowired
    private UserDataInitializer userDataInitializer;

    @Autowired
    private PasswordCredentials passwordCredentials;

    @Autowired
    protected StringRedisTemplate redis;

    protected static final long ADMIN_ID = LoginUserConfig.ADMIN_ID;

    @BeforeEach
    void resetClockAndRedis() {
        clock.reset();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    /** 회원의 비밀번호를 정한다 (가입·비밀번호 변경 API 전이라 직접) */
    protected void setPassword(long userId, String rawPassword) {
        passwordCredentials.set(userId, rawPassword);
    }

    protected long defaultCategoryId() {
        return defaultCategoryId(ADMIN_ID);
    }

    protected long defaultCategoryId(long userId) {
        return jdbc.queryForObject("SELECT category_id FROM categories WHERE user_id = ? AND is_default = 'Y' AND deleted = 'N'",
            Long.class, userId);
    }

    /** 회원을 하나 더 만든다 (가입한 것처럼 기본 데이터 포함). 가입 API(US-33)가 생기기 전이라 DB에 직접 넣는다 */
    protected long createUser(String email) {
        jdbc.update("INSERT INTO users (email, name, status) VALUES (?, '회원', 'ACTIVE')", email);
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
        userDataInitializer.createDefaults(userId);
        return userId;
    }

    /** 이 요청을 그 회원이 로그인한 것으로 보낸다 */
    protected static RequestPostProcessor as(long userId) {
        return SecurityMockMvcRequestPostProcessors.authentication(new AuthUser(userId).toAuthentication());
    }

    /** 이 요청을 로그인 없이 보낸다 */
    protected static RequestPostProcessor anonymous() {
        return SecurityMockMvcRequestPostProcessors.anonymous();
    }
}
