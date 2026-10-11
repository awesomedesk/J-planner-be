package com.awesomedesk.j_planner.common.db;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.awesomedesk.j_planner.config.FlywayConfig;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** V2가 쓰는 admin 이메일(JP_ADMIN_EMAIL)이 비어 있으면 서버가 시작하지 않는다 — 이메일 '' admin이 생기지 않게 (PO 제안, US-32) */
class AdminEmailPlaceholderTest {

    private static FluentConfiguration config(String email) {
        return Flyway.configure().placeholders(Map.of("admin_email", email));
    }

    @Test
    @DisplayName("빈 값·공백이면 시작을 멈춘다")
    void blankStops() {
        assertThatThrownBy(() -> new FlywayConfig().requireAdminEmail().customize(config("")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("JP_ADMIN_EMAIL");
        assertThatThrownBy(() -> new FlywayConfig().requireAdminEmail().customize(config("   ")))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FlywayConfig().requireAdminEmail().customize(Flyway.configure()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이메일 모양이면 그대로 둔다")
    void validPasses() {
        assertThatCode(() -> new FlywayConfig().requireAdminEmail().customize(config("owner@example.com")))
            .doesNotThrowAnyException();
    }
}
