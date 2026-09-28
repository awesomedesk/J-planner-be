package com.awesomedesk.j_planner.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesomedesk.j_planner.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DB 테이블은 Flyway 마이그레이션(db/migration/V*.sql)으로 만든다.
 * 서버가 켜질 때 아직 적용 안 된 변경만 자동 적용 → 테스트·운영 DB를 손으로 고치지 않는다.
 */
class FlywayMigrationTest extends IntegrationTest {

    @Test
    @DisplayName("V1(초기 테이블)이 적용 기록(flyway_schema_history)에 성공으로 남는다")
    void v1Applied() {
        List<String> versions = jdbc.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank",
            String.class);
        assertThat(versions).startsWith("1");
    }

    @Test
    @DisplayName("MVP 테이블 9개가 모두 있다")
    void tables() {
        List<String> tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);
        assertThat(tables).contains("categories", "calendars", "calendar_details", "todos", "ddays", "diaries", "memos",
            "user_settings", "sidebar_items");
    }
}
