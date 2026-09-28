package com.awesomedesk.j_planner.api.v1.settings;

import com.awesomedesk.j_planner.common.error.ApiException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 설정의 값 사이 규칙 (08-api-design.md 9절). 필드 하나의 규칙은 {@link SettingsRequest}의 Bean Validation */
final class SettingsRules {

    private static final Set<SidebarItemType> SIDEBAR_TYPES = Set.of(SidebarItemType.values());

    private SettingsRules() {
    }

    static void check(SettingsRequest r) {
        checkSlotMinutes(r.slotMinutes());
        checkSidebarItems(r.sidebarItems());
    }

    static void checkSlotMinutes(int slotMinutes) {
        if (slotMinutes != 30 && slotMinutes != 60) {
            throw ApiException.validation("slotMinutes", "칸 간격은 30분 또는 60분입니다.");
        }
    }

    /** 4개 모두, 한 번씩 (배열 순서 = 표시 순서) */
    static void checkSidebarItems(List<SettingsRequest.SidebarItem> items) {
        Set<SidebarItemType> types = items.stream().map(SettingsRequest.SidebarItem::type).collect(Collectors.toSet());
        if (items.size() != SIDEBAR_TYPES.size() || !types.equals(SIDEBAR_TYPES)) {
            throw ApiException.validation("sidebarItems", "사이드바 항목 TODO, DDAY, DIARY, MEMO를 한 번씩 모두 보내세요.");
        }
    }
}
