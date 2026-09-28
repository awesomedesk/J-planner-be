package com.awesomedesk.j_planner.support;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 테스트 DB(jp_test)는 테스트를 시작할 때 비우고(clean) 마이그레이션을 처음부터 적용한다.
 * → 마이그레이션 파일만으로 테이블이 제대로 만들어지는지 매번 확인된다. (운영에서는 clean 금지: clean-disabled 기본값 true)
 */
@TestConfiguration
public class TestFlywayConfig {

    @Bean
    public FlywayMigrationStrategy cleanMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
