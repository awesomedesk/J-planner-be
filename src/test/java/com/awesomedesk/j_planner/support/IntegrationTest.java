package com.awesomedesk.j_planner.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실제 MySQL(테스트 DB jp_test)로 API 전체를 확인하는 테스트의 부모.
 * 테이블은 schema.sql로 만들고(src/test/resources/application.yml), 테스트마다 sql/reset.sql로 데이터를 비운다.
 * "지금"은 FixedClockConfig의 2026-09-25 09:00 (한국 시간)으로 고정한다.
 * 로컬 MySQL에 jp_test를 만들 수 있는 계정이 있어야 한다 (JP_TEST_DB_URL / USERNAME / PASSWORD로 바꿀 수 있음).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@Sql(scripts = "classpath:sql/reset.sql")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    protected long defaultCategoryId() {
        return jdbc.queryForObject("SELECT category_id FROM categories WHERE is_default = 'Y' AND deleted = 'N'", Long.class);
    }
}
