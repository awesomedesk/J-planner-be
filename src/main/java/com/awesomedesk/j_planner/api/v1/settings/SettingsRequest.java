package com.awesomedesk.j_planner.api.v1.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 설정 PATCH를 현재 값에 합친 결과 (08-api-design.md 9절). 모든 항목이 필수라 null로 지울 수 없다.
 * 필드 사이 규칙(시간표 시작&lt;끝, 칸 간격, 사이드바 4개)은 {@link SettingsRules}.
 */
public record SettingsRequest(
    @NotNull(message = "주 시작 요일을 고르세요.")
    @Pattern(regexp = "SUN|MON", message = "주 시작 요일은 SUN 또는 MON입니다.")
    String weekStartDay,

    @NotNull(message = "처음 화면을 고르세요.")
    @Pattern(regexp = "MONTH|WEEK|DAY|LAST", message = "처음 화면은 MONTH, WEEK, DAY, LAST 중 하나입니다.")
    String startView,

    @NotNull(message = "시간 표시 형식을 고르세요.")
    @Pattern(regexp = "24H|12H", message = "시간 표시는 24H 또는 12H입니다.")
    String timeFormat,

    @NotNull(message = "시간표 시작 시각을 고르세요.")
    @Min(value = 0, message = "시간표 시작은 0~23시입니다.")
    @Max(value = 23, message = "시간표 시작은 0~23시입니다.")
    Integer timetableStartHour,

    @NotNull(message = "시간표 끝 시각을 고르세요.")
    @Min(value = 1, message = "시간표 끝은 1~24시입니다.")
    @Max(value = 24, message = "시간표 끝은 1~24시입니다.")
    Integer timetableEndHour,

    @NotNull(message = "칸 간격을 고르세요.")
    Integer slotMinutes,

    @NotNull(message = "다크 모드를 켜거나 끄세요.")
    Boolean darkMode,

    @NotNull(message = "색 테마를 고르세요.")
    @Pattern(regexp = "GREEN|BROWN|GRAY", message = "색 테마는 GREEN, BROWN, GRAY 중 하나입니다.")
    String colorTheme,

    @NotNull(message = "사이드바 열림 상태가 필요합니다.")
    Boolean sidebarOpen,

    @NotNull(message = "사이드바 항목 4개를 순서대로 보내세요.")
    List<@Valid @NotNull SidebarItem> sidebarItems
) {

    public record SidebarItem(
        @NotNull(message = "항목 종류가 필요합니다.")
        @Pattern(regexp = "TODO|DDAY|DIARY|MEMO", message = "항목은 TODO, DDAY, DIARY, MEMO 중 하나입니다.")
        String type,

        @NotNull(message = "표시 여부가 필요합니다.")
        Boolean visible
    ) {
    }
}
