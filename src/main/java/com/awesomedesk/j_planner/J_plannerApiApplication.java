package com.awesomedesk.j_planner;

import com.awesomedesk.j_planner.common.aop.logger.LoggerAspect;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@Configuration
@Import(LoggerAspect.class)
public class J_plannerApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(J_plannerApiApplication.class, args);
    }
}
