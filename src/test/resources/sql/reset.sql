-- 테스트마다 데이터를 비우고 기본 데이터(미지정·설정·사이드바)만 남긴다. 테이블 구조는 schema.sql 그대로.
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE sidebar_items;
TRUNCATE TABLE user_settings;
TRUNCATE TABLE memos;
TRUNCATE TABLE diaries;
TRUNCATE TABLE ddays;
TRUNCATE TABLE todos;
TRUNCATE TABLE calendar_details;
TRUNCATE TABLE calendars;
TRUNCATE TABLE categories;
SET FOREIGN_KEY_CHECKS = 1;
INSERT INTO categories (name, color, is_default, sort_order) VALUES ('미지정', '#6B6B6B', 'Y', 0);
INSERT INTO user_settings (setting_id) VALUES (1);
INSERT INTO sidebar_items (setting_id, item_type, visible, sort_order)
VALUES (1, 'TODO', 'Y', 0), (1, 'DDAY', 'Y', 1), (1, 'DIARY', 'Y', 2), (1, 'MEMO', 'Y', 3);
