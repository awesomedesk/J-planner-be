package com.awesomedesk.j_planner.config;

import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * V2 마이그레이션이 쓰는 admin 계정 이메일(자리표시자 admin_email ← 환경 변수 JP_ADMIN_EMAIL)이 비어 있으면
 * 서버를 시작하지 않는다. 비어 있는 채로 V2가 적용되면 이메일 '' admin이 생기기 때문 (07-db-design.md 11-5).
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayConfigurationCustomizer requireAdminEmail() {
        return configuration -> {
            String email = configuration.getPlaceholders().get("admin_email");
            if (email == null || email.isBlank()) {
                throw new IllegalStateException(
                    "JP_ADMIN_EMAIL(Flyway 자리표시자 admin_email)이 비어 있습니다. 기존 데이터를 옮길 admin 계정 이메일을 설정하세요.");
            }
        };
    }
}
