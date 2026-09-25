package com.awesomedesk.j_planner.support;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 테스트의 "지금" = 2026-09-25(금) 09:00 한국 시간 */
@TestConfiguration
public class FixedClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final ZonedDateTime NOW = ZonedDateTime.of(2026, 9, 25, 9, 0, 0, 0, ZONE);

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(NOW.toInstant(), ZONE);
    }
}
