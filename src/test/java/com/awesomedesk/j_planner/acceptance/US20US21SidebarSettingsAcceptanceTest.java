package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-20 사용자는 사이드바를 열고 닫는다 / US-21 사이드바 항목의 표시·순서를 정한다 (09-backlog.md)
 * <ul>
 *   <li>US-20 AC1 아이콘 막대·섹션 열기 → FE 담당. 열림/닫힘 상태 기억(서버 저장)만 여기서</li>
 *   <li>US-20 AC2 태블릿 겹침 닫기 → FE 담당</li>
 *   <li>US-21 AC1 ⚙ 설정 모드·'완료' 버튼·월·주·일·모바일 시트 적용 → FE 담당. 켜기/끄기·순서 서버 저장만 여기서</li>
 * </ul>
 */
@DisplayName("US-20·21 사이드바 열기/닫기와 항목 설정")
class US20US21SidebarSettingsAcceptanceTest extends AcceptanceTest {

    private static final String URL = "/api/v1/settings";

    @Test
    @DisplayName("US-20 AC1: 열림/닫힘 상태를 기억한다 (처음엔 열림, D-021)")
    void sidebarOpenRemembered() throws Exception {
        getJson(URL).andExpect(jsonPath("$.sidebarOpen").value(true));

        patchJson(URL, "{\"sidebarOpen\":false}").andExpect(status().isOk());
        getJson(URL).andExpect(jsonPath("$.sidebarOpen").value(false));

        patchJson(URL, "{\"sidebarOpen\":true}").andExpect(status().isOk());
        getJson(URL).andExpect(jsonPath("$.sidebarOpen").value(true));
    }

    @Test
    @DisplayName("US-21 AC1: 처음엔 Todo·D-Day·일기·메모 모두 표시, 이 순서")
    void defaultItems() throws Exception {
        getJson(URL)
            .andExpect(jsonPath("$.sidebarItems[*].type", contains("TODO", "DDAY", "DIARY", "MEMO")))
            .andExpect(jsonPath("$.sidebarItems[*].visible", contains(true, true, true, true)));
    }

    @Test
    @DisplayName("US-21 AC1: 항목 켜기/끄기와 ▲▼ 순서를 서버에 저장한다")
    void saveVisibilityAndOrder() throws Exception {
        patchJson(URL, """
            {"sidebarItems":[
              {"type":"MEMO","visible":true},{"type":"TODO","visible":true},
              {"type":"DIARY","visible":false},{"type":"DDAY","visible":true}]}
            """).andExpect(status().isOk());

        getJson(URL)
            .andExpect(jsonPath("$.sidebarItems[*].type", contains("MEMO", "TODO", "DIARY", "DDAY")))
            .andExpect(jsonPath("$.sidebarItems[*].visible", contains(true, true, false, true)));
    }

    @Test
    @DisplayName("US-21 AC1: 항목은 넷 모두 한 번씩 보내야 한다 (빠짐·중복 → 400, 저장 안 됨)")
    void mustSendAllFour() throws Exception {
        patchJson(URL, "{\"sidebarItems\":[{\"type\":\"TODO\",\"visible\":true},{\"type\":\"DDAY\",\"visible\":true},{\"type\":\"DIARY\",\"visible\":true}]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        patchJson(URL, """
            {"sidebarItems":[{"type":"TODO","visible":true},{"type":"TODO","visible":false},
                             {"type":"DIARY","visible":true},{"type":"MEMO","visible":true}]}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("sidebarItems"));

        getJson(URL).andExpect(jsonPath("$.sidebarItems[*].type", contains("TODO", "DDAY", "DIARY", "MEMO")));
    }
}
