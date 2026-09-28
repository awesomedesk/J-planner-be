package com.awesomedesk.j_planner.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.support.IntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * DB가 직접 기록하는 시각(삭제 시각 NOW() 등)도 한국 시각이어야 한다 (D-040과 같은 기준).
 * DB 서버 시간대가 UTC여도 연결마다 세션 시간대를 +09:00으로 맞춘다.
 */
class DbTimeZoneTest extends IntegrationTest {

    @Test
    @DisplayName("DB 연결의 세션 시간대는 +09:00")
    void sessionTimeZone() {
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+09:00");
    }

    @Test
    @DisplayName("삭제 시각(deleted_at, DB의 NOW())은 지금 한국 시각")
    void deletedAtIsSeoulTime() throws Exception {
        String body = mvc.perform(post("/api/v1/todos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\"}"))
            .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
        mvc.perform(delete("/api/v1/todos/" + id)).andExpect(status().isNoContent());

        LocalDateTime deletedAt = jdbc.queryForObject("SELECT deleted_at FROM todos WHERE todo_id = ?", LocalDateTime.class, id);
        LocalDateTime seoulNow = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        assertThat(Duration.between(deletedAt, seoulNow).abs()).isLessThan(Duration.ofMinutes(2));
    }
}
