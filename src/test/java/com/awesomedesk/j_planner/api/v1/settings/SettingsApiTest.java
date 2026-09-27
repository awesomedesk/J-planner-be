package com.awesomedesk.j_planner.api.v1.settings;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 설정 API 세부 규칙 (08-api-design.md 9절) */
class SettingsApiTest extends IntegrationTest {

    private ResultActions patchSettings(String json) throws Exception {
        return mvc.perform(patch("/api/v1/settings").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void expectFieldError(String json, String field) throws Exception {
        patchSettings(json)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value(field));
    }

    @Test
    @DisplayName("PATCH 응답은 설정 전체")
    void patchReturnsAll() throws Exception {
        patchSettings("{\"darkMode\":true}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.darkMode").value(true))
            .andExpect(jsonPath("$.startView").value("MONTH"))
            .andExpect(jsonPath("$.sidebarItems.length()").value(4));
    }

    @Test
    @DisplayName("값 범위·형식 오류 → 400 + 필드")
    void invalidValues() throws Exception {
        expectFieldError("{\"weekStartDay\":\"TUE\"}", "weekStartDay");
        expectFieldError("{\"timeFormat\":\"24\"}", "timeFormat");
        expectFieldError("{\"colorTheme\":\"PINK\"}", "colorTheme");
        expectFieldError("{\"timetableStartHour\":24}", "timetableStartHour");
        expectFieldError("{\"timetableEndHour\":0}", "timetableEndHour");
        expectFieldError("{\"timetableEndHour\":25}", "timetableEndHour");
        expectFieldError("{\"slotMinutes\":45}", "slotMinutes");
        expectFieldError("{\"sidebarItems\":[{\"type\":\"CALENDAR\",\"visible\":true},{\"type\":\"DDAY\",\"visible\":true},{\"type\":\"DIARY\",\"visible\":true},{\"type\":\"MEMO\",\"visible\":true}]}", "sidebarItems[0].type");
    }

    @Test
    @DisplayName("시간표 끝은 시작보다 뒤 (같거나 앞이면 400 timetableEndHour)")
    void timetableOrder() throws Exception {
        expectFieldError("{\"timetableStartHour\":10,\"timetableEndHour\":10}", "timetableEndHour");
        expectFieldError("{\"timetableStartHour\":23,\"timetableEndHour\":22}", "timetableEndHour");
        patchSettings("{\"timetableStartHour\":0,\"timetableEndHour\":1}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("null은 지울 수 없다 → 400 (설정은 모두 필수값)")
    void nullRejected() throws Exception {
        expectFieldError("{\"darkMode\":null}", "darkMode");
        expectFieldError("{\"sidebarItems\":null}", "sidebarItems");
    }

    @Test
    @DisplayName("오류가 나면 아무것도 저장되지 않는다")
    void atomic() throws Exception {
        patchSettings("{\"darkMode\":true,\"slotMinutes\":45}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/settings")).andExpect(jsonPath("$.darkMode").value(false));
    }
}
