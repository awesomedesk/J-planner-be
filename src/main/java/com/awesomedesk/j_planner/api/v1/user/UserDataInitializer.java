package com.awesomedesk.j_planner.api.v1.user;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새 회원의 기본 데이터 (07-db-design.md 11-4). V1 기본 데이터와 같은 값을 회원마다 만든다:
 * '미지정' 카테고리 1행, 설정 1행(모두 기본값), 사이드바 4행(Todo → D-Day → 일기 → 메모, 모두 표시).
 * 가입(US-33)·구글 가입(US-36)이 회원을 만든 같은 트랜잭션에서 부른다.
 */
@Component
@RequiredArgsConstructor
public class UserDataInitializer {

    static final String DEFAULT_CATEGORY_NAME = "미지정";
    static final String DEFAULT_CATEGORY_COLOR = "#6B6B6B";
    static final String[] SIDEBAR_ITEMS = {"TODO", "DDAY", "DIARY", "MEMO"};

    private final JdbcTemplate jdbc;

    @Transactional
    public void createDefaults(long userId) {
        jdbc.update("INSERT INTO categories (user_id, name, color, is_default, sort_order) VALUES (?, ?, ?, 'Y', 0)",
            userId, DEFAULT_CATEGORY_NAME, DEFAULT_CATEGORY_COLOR);
        jdbc.update("INSERT INTO user_settings (user_id) VALUES (?)", userId);
        for (int i = 0; i < SIDEBAR_ITEMS.length; i++) {
            jdbc.update("INSERT INTO sidebar_items (user_id, item_type, visible, sort_order) VALUES (?, ?, 'Y', ?)",
                userId, SIDEBAR_ITEMS[i], i);
        }
    }
}
