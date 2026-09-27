package com.awesomedesk.j_planner.acceptance;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-26 사용자는 설정에서 달력·시간표·테마를 바꾼다 / US-27 처음 화면을 고른다 (09-backlog.md)
 * <ul>
 *   <li>US-26 AC1 설정 패널·전체 화면 → FE 담당</li>
 *   <li>US-26 AC2 중 사이드바 항목은 US-21, 카테고리 관리는 US-04 테스트</li>
 *   <li>US-27 AC2 '마지막에 본 화면' 값 기억(브라우저 localStorage)·3일/목록 시작 → FE 담당 (D-029, D-030)</li>
 * </ul>
 */
@DisplayName("US-26·27 설정과 처음 화면")
class US26US27SettingsAcceptanceTest extends AcceptanceTest {

    private static final String URL = "/api/v1/settings";

    @Test
    @DisplayName("US-26 AC2: 항목과 기본값 — 주 시작 일요일, 24시간, 06~24시, 1시간 칸, 라이트, 녹색 테마 (D-024)")
    void defaults() throws Exception {
        getJson(URL)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.weekStartDay").value("SUN"))
            .andExpect(jsonPath("$.startView").value("MONTH"))
            .andExpect(jsonPath("$.timeFormat").value("24H"))
            .andExpect(jsonPath("$.timetableStartHour").value(6))
            .andExpect(jsonPath("$.timetableEndHour").value(24))
            .andExpect(jsonPath("$.slotMinutes").value(60))
            .andExpect(jsonPath("$.darkMode").value(false))
            .andExpect(jsonPath("$.colorTheme").value("GREEN"));
    }

    @Test
    @DisplayName("US-26 AC2·3: 모든 항목을 바꿀 수 있고, 바꾸면 즉시 저장된다 (SET-05)")
    void changeAll() throws Exception {
        patchJson(URL, """
            {"weekStartDay":"MON","timeFormat":"12H","timetableStartHour":8,"timetableEndHour":22,
             "slotMinutes":30,"darkMode":true,"colorTheme":"BROWN"}
            """).andExpect(status().isOk())
            .andExpect(jsonPath("$.weekStartDay").value("MON"));

        getJson(URL)
            .andExpect(jsonPath("$.weekStartDay").value("MON"))
            .andExpect(jsonPath("$.timeFormat").value("12H"))
            .andExpect(jsonPath("$.timetableStartHour").value(8))
            .andExpect(jsonPath("$.timetableEndHour").value(22))
            .andExpect(jsonPath("$.slotMinutes").value(30))
            .andExpect(jsonPath("$.darkMode").value(true))
            .andExpect(jsonPath("$.colorTheme").value("BROWN"));
    }

    @Test
    @DisplayName("US-26 AC3: 바꾼 항목만 저장되고 나머지는 그대로")
    void onlyChangedField() throws Exception {
        patchJson(URL, "{\"colorTheme\":\"GRAY\"}").andExpect(status().isOk());
        patchJson(URL, "{\"darkMode\":true}").andExpect(status().isOk());

        getJson(URL)
            .andExpect(jsonPath("$.colorTheme").value("GRAY"))
            .andExpect(jsonPath("$.darkMode").value(true))
            .andExpect(jsonPath("$.weekStartDay").value("SUN"))
            .andExpect(jsonPath("$.sidebarItems.length()").value(4));
    }

    @Test
    @DisplayName("US-26 AC2: 주 시작 요일을 바꾸면 새 주간 Todo가 그 요일을 따른다 (CAL-04, D-041)")
    void weekStartAffectsWeekTodo() throws Exception {
        patchJson(URL, "{\"weekStartDay\":\"MON\"}").andExpect(status().isOk());

        postJson("/api/v1/todos", "{\"title\":\"주간\",\"type\":\"WEEK\",\"startDate\":\"2026-09-21\",\"endDate\":\"2026-09-27\"}")
            .andExpect(status().isCreated());
        postJson("/api/v1/todos", "{\"title\":\"주간\",\"type\":\"WEEK\",\"startDate\":\"2026-09-20\",\"endDate\":\"2026-09-26\"}")
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("US-27 AC1: 처음 화면은 월간 / 주간 / 일간 / 마지막에 본 화면 중 하나 (기본 월간)")
    void startView() throws Exception {
        for (String view : new String[] {"WEEK", "DAY", "LAST", "MONTH"}) {
            patchJson(URL, "{\"startView\":\"" + view + "\"}").andExpect(status().isOk());
            getJson(URL).andExpect(jsonPath("$.startView").value(view));
        }
        patchJson(URL, "{\"startView\":\"THREE_DAYS\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("startView"));
    }
}
