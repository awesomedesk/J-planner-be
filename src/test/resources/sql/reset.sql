-- 테스트마다 데이터를 비우고 admin 회원(user_id = 1)과 그 기본 데이터(미지정·설정·사이드바)만 남긴다.
-- 테이블 구조는 Flyway 마이그레이션(db/migration) 그대로.
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
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;
INSERT INTO users (user_id, email, name, status) VALUES (1, 'admin@test.local', '관리자', 'ACTIVE');
INSERT INTO categories (user_id, name, color, is_default, sort_order) VALUES (1, '미지정', '#6B6B6B', 'Y', 0);
INSERT INTO user_settings (user_id) VALUES (1);
INSERT INTO sidebar_items (user_id, item_type, visible, sort_order)
VALUES (1, 'TODO', 'Y', 0), (1, 'DDAY', 'Y', 1), (1, 'DIARY', 'Y', 2), (1, 'MEMO', 'Y', 3);
