package com.awesomedesk.j_planner.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 설정. created_at·updated_at은 {@link Clock} 빈(Asia/Seoul) 기준으로 초 단위까지 기록한다.
 * → 서버(JVM) 시간대와 관계없이 한국 시각으로 저장되고(D-040), 테스트에서는 고정 시계를 따른다.
 * 추가 설정이 필요한 경우 config/README.md 참고
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock).withNano(0));
    }
}
