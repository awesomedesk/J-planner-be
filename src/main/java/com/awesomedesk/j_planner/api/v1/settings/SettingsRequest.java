package com.awesomedesk.j_planner.api.v1.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 설정 PATCH를 현재 값에 합친 결과 (08-api-design.md 9절). 모든 항목이 필수라 null로 지울 수 없다.
 * 값 사이 규칙(칸 간격, 사이드바 4개)은 {@link SettingsRules}.
 */
public record SettingsRequest(
    @NotNull(message = "주 시작 요일을 고르세요.")
    WeekStartDay weekStartDay,

    @NotNull(message = "처음 화면을 고르세요.")
    StartView startView,

    @NotNull(message = "시간 표시 형식을 고르세요.")
    TimeFormat timeFormat,

    @NotNull(message = "칸 간격을 고르세요.")
    Integer slotMinutes,

    @NotNull(message = "다크 모드를 켜거나 끄세요.")
    Boolean darkMode,

    @NotNull(message = "색 테마를 고르세요.")
    ColorTheme colorTheme,

    @NotNull(message = "사이드바 열림 상태가 필요합니다.")
    Boolean sidebarOpen,

    @NotNull(message = "사이드바 항목 4개를 순서대로 보내세요.")
    List<@Valid @NotNull SidebarItem> sidebarItems
) {

    public record SidebarItem(
        @NotNull(message = "항목 종류가 필요합니다.")
        SidebarItemType type,

        @NotNull(message = "표시 여부가 필요합니다.")
        Boolean visible
    ) {
    }
}
