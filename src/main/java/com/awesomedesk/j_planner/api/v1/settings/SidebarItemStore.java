package com.awesomedesk.j_planner.api.v1.settings;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 사이드바 항목 (07-db-design.md 4-10). 항상 4개를 통째로 바꾸므로(08 9절) 지우고 다시 넣는다.
 * 기본 키가 (user_id, item_type)이라 JPA 컬렉션으로 순서를 바꾸면 키가 잠깐 겹칠 수 있어 SQL로 직접 다룬다.
 */
@Repository
@RequiredArgsConstructor
class SidebarItemStore {

    private final JdbcTemplate jdbc;

    List<SettingsResponse.SidebarItem> find(long userId) {
        return jdbc.query(
            "SELECT item_type, visible FROM sidebar_items WHERE user_id = ? ORDER BY sort_order",
            (rs, i) -> new SettingsResponse.SidebarItem(SidebarItemType.valueOf(rs.getString("item_type")), "Y".equals(rs.getString("visible"))),
            userId);
    }

    void replace(long userId, List<SettingsRequest.SidebarItem> items) {
        jdbc.update("DELETE FROM sidebar_items WHERE user_id = ?", userId);
        for (int i = 0; i < items.size(); i++) {
            SettingsRequest.SidebarItem item = items.get(i);
            jdbc.update("INSERT INTO sidebar_items (user_id, item_type, visible, sort_order) VALUES (?, ?, ?, ?)",
                userId, item.type().name(), item.visible() ? "Y" : "N", i);
        }
    }
}
