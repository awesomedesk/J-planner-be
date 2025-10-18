package com.awesomedesk.j_planner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 설정
 * 추가 설정이 필요한 경우 config/README.md 참고
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
