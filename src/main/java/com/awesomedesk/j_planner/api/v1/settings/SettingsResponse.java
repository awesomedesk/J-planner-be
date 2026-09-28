package com.awesomedesk.j_planner.api.v1.settings;

import java.util.List;

/** 설정 (08-api-design.md 9절). sidebarItems 배열 순서 = 표시 순서 */
public record SettingsResponse(
    WeekStartDay weekStartDay,
    StartView startView,
    TimeFormat timeFormat,
    int slotMinutes,
    boolean darkMode,
    ColorTheme colorTheme,
    boolean sidebarOpen,
    List<SidebarItem> sidebarItems
) {

    public record SidebarItem(SidebarItemType type, boolean visible) {
    }

    static SettingsResponse of(UserSettings s, List<SidebarItem> items) {
        return new SettingsResponse(s.getWeekStartDay(), s.getStartView(), s.getTimeFormat(),
            s.getSlotMinutes(), s.isDarkMode(), s.getColorTheme(), s.isSidebarOpen(), items);
    }

    /** PATCH 합치기용: 현재 값을 요청 모양으로 */
    SettingsRequest toRequest() {
        return new SettingsRequest(weekStartDay, startView, timeFormat, slotMinutes,
            darkMode, colorTheme, sidebarOpen,
            sidebarItems.stream().map(i -> new SettingsRequest.SidebarItem(i.type(), i.visible())).toList());
    }
}
