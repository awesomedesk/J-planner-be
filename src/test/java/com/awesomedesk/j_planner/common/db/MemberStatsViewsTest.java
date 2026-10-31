package com.awesomedesk.j_planner.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesomedesk.j_planner.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 어드민 회원 현황(ADM-04, US-42 작업 12)이 읽는 집계 뷰 (V9, ADMIN #300). 개인정보 없이 숫자만 — Grafana(jp_grafana)는 이 뷰만 SELECT.
 * 날짜별 숫자는 audit_logs에서 세므로 1년(보관 기간)까지만 남는다.
 */
class MemberStatsViewsTest extends IntegrationTest {

    @Test
    @DisplayName("v_member_daily: 날짜별 가입·탈퇴(reason 없는 ACCOUNT_DELETED)·휴면·복원·휴면 4년 삭제(reason DORMANT_EXPIRED) 수")
    void daily() {
        audit(10, "SIGNUP_COMPLETED", "{\"method\":\"EMAIL\"}", "2026-09-24 10:00:00");
        audit(11, "SIGNUP_COMPLETED", "{\"method\":\"GOOGLE\"}", "2026-09-24 23:59:59");
        audit(12, "ACCOUNT_DELETED", null, "2026-09-24 12:00:00");
        audit(13, "ACCOUNT_DELETED", "{\"reason\":\"DORMANT_EXPIRED\"}", "2026-09-24 04:10:00");
        audit(14, "DORMANT", null, "2026-09-24 04:10:00");
        audit(15, "DORMANT_RESTORED", null, "2026-09-24 08:00:00");
        audit(16, "LOGIN", "{\"method\":\"PASSWORD\"}", "2026-09-24 08:00:00");
        audit(17, "SIGNUP_COMPLETED", "{\"method\":\"EMAIL\"}", "2026-09-25 00:00:00");

        Map<String, Object> day = jdbc.queryForMap("SELECT * FROM v_member_daily WHERE stat_date = '2026-09-24'");
        assertThat(day).containsOnlyKeys("stat_date", "signups", "withdrawals", "dormant", "restored", "dormant_deleted");
        assertThat(((Number) day.get("signups")).intValue()).isEqualTo(2);
        assertThat(((Number) day.get("withdrawals")).intValue()).isEqualTo(1);
        assertThat(((Number) day.get("dormant")).intValue()).isEqualTo(1);
        assertThat(((Number) day.get("restored")).intValue()).isEqualTo(1);
        assertThat(((Number) day.get("dormant_deleted")).intValue()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT signups FROM v_member_daily WHERE stat_date = '2026-09-25'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("v_member_status: 지금 활성(정지 아님)·휴면·정지 수, 전체 — 한 행")
    void status() {
        jdbc.update("INSERT INTO users (email, name, status) VALUES ('a@test.local', 'a', 'ACTIVE')");
        jdbc.update("INSERT INTO users (email, name, status, suspended_at) VALUES ('s@test.local', 's', 'ACTIVE', NOW())");
        jdbc.update("INSERT INTO users (email, name, status, dormant_at) VALUES (NULL, NULL, 'DORMANT', NOW())");
        jdbc.update("INSERT INTO users (email, name, status, dormant_at, suspended_at) VALUES (NULL, NULL, 'DORMANT', NOW(), NOW())");

        Map<String, Object> now = jdbc.queryForMap("SELECT * FROM v_member_status");
        assertThat(now).containsOnlyKeys("active", "dormant", "suspended", "total");
        // admin(reset.sql에서 넣는 회원 1) + a = 활성 2
        assertThat(((Number) now.get("active")).intValue()).isEqualTo(2);
        assertThat(((Number) now.get("dormant")).intValue()).isEqualTo(2);
        assertThat(((Number) now.get("suspended")).intValue()).isEqualTo(2);
        assertThat(((Number) now.get("total")).intValue()).isEqualTo(5);
    }

    private void audit(long userId, String event, String detail, String at) {
        jdbc.update("INSERT INTO audit_logs (user_id, actor, event, detail, created_at) VALUES (?, 'SYSTEM', ?, ?, ?)", userId, event, detail, at);
    }
}
