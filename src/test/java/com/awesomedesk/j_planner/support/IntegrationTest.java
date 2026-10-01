package com.awesomedesk.j_planner.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실제 MySQL(테스트 DB jp_test)로 API 전체를 확인하는 테스트의 부모.
 * 테이블은 Flyway 마이그레이션으로 만들고(TestFlywayConfig), 테스트마다 sql/reset.sql로 데이터를 비운다.
 * 모든 응답은 명세(08-openapi.yaml)와 자동 대조한다 (OpenApiContract).
 * "지금"은 FixedClockConfig의 2026-09-25 09:00 (한국 시간)으로 고정한다.
 * 로컬 MySQL에 jp_test를 만들 수 있는 계정이 있어야 한다 (JP_TEST_DB_URL / USERNAME / PASSWORD로 바꿀 수 있음).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({FixedClockConfig.class, TestFlywayConfig.class, OpenApiContract.class})
@Sql(scripts = "classpath:sql/reset.sql")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TestClock clock;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    protected long defaultCategoryId() {
        return jdbc.queryForObject("SELECT category_id FROM categories WHERE is_default = 'Y' AND deleted = 'N'", Long.class);
    }
}
