package com.awesomedesk.j_planner.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "지금"·"오늘"은 한국 시간 기준 (08-api-design.md 2-2절). 테스트에서는 고정 Clock으로 바꿔 쓴다.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
