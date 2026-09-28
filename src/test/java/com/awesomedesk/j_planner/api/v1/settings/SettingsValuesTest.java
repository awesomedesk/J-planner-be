package com.awesomedesk.j_planner.api.v1.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 설정 값은 정해진 목록(enum). JSON·DB 글자는 08-api-design.md 9절 그대로 — DB 없이 */
class SettingsValuesTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("JSON 글자: 24H·12H, SUN·MON, MONTH·LAST, GREEN·GRAY")
    void jsonValues() {
        assertThat(mapper.writeValueAsString(TimeFormat.H24)).isEqualTo("\"24H\"");
        assertThat(mapper.readValue("\"12H\"", TimeFormat.class)).isEqualTo(TimeFormat.H12);
        assertThat(mapper.readValue("\"MON\"", WeekStartDay.class)).isEqualTo(WeekStartDay.MON);
        assertThat(mapper.readValue("\"LAST\"", StartView.class)).isEqualTo(StartView.LAST);
        assertThat(mapper.readValue("\"GRAY\"", ColorTheme.class)).isEqualTo(ColorTheme.GRAY);
    }

    @Test
    @DisplayName("주 시작 요일 → java DayOfWeek")
    void weekStart() {
        assertThat(WeekStartDay.SUN.toDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(WeekStartDay.MON.toDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }
}
