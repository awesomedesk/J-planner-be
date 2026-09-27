package com.awesomedesk.j_planner.api.v1.settings;

import static com.awesomedesk.j_planner.support.ApiExceptionAssertions.assertFieldError;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 설정의 필드 사이 규칙 — DB 없이 */
class SettingsRulesTest {

    private static List<SettingsRequest.SidebarItem> items(String... types) {
        return java.util.Arrays.stream(types).map(t -> new SettingsRequest.SidebarItem(t, true)).toList();
    }

    @Test
    @DisplayName("사이드바 항목: 넷이 한 번씩이면 순서는 자유")
    void sidebarOk() {
        assertThatCode(() -> SettingsRules.checkSidebarItems(items("MEMO", "DIARY", "DDAY", "TODO")))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("사이드바 항목: 빠지거나 중복되면 sidebarItems 오류")
    void sidebarInvalid() {
        assertFieldError(() -> SettingsRules.checkSidebarItems(items("TODO", "DDAY", "DIARY")), "sidebarItems");
        assertFieldError(() -> SettingsRules.checkSidebarItems(items("TODO", "TODO", "DIARY", "MEMO")), "sidebarItems");
        assertFieldError(() -> SettingsRules.checkSidebarItems(items("TODO", "DDAY", "DIARY", "MEMO", "MEMO")), "sidebarItems");
    }

    @Test
    @DisplayName("시간표: 시작 < 끝")
    void timetable() {
        assertThatCode(() -> SettingsRules.checkTimetable(6, 24)).doesNotThrowAnyException();
        assertThatCode(() -> SettingsRules.checkTimetable(0, 1)).doesNotThrowAnyException();
        assertFieldError(() -> SettingsRules.checkTimetable(10, 10), "timetableEndHour");
        assertFieldError(() -> SettingsRules.checkTimetable(23, 22), "timetableEndHour");
    }

    @Test
    @DisplayName("칸 간격: 30분 또는 60분")
    void slotMinutes() {
        assertThatCode(() -> SettingsRules.checkSlotMinutes(30)).doesNotThrowAnyException();
        assertThatCode(() -> SettingsRules.checkSlotMinutes(60)).doesNotThrowAnyException();
        assertFieldError(() -> SettingsRules.checkSlotMinutes(45), "slotMinutes");
    }
}
