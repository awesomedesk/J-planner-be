-- ============================================================
-- V2: 회원(users) + 모든 데이터를 사용자별로 (US-32, D-060·D-061)
-- 설계 문서: j-planner-product/07-db-design.md 11절 (11-2 users, 11-3 기존 테이블 변경, 11-5 admin 이전, 11-7 인덱스)
--
-- 기존 1인 데이터는 admin 계정 1행으로 옮긴다.
--   admin 이메일 = Flyway 자리표시자 ${admin_email} (설정 spring.flyway.placeholders.admin_email ← 환경 변수 JP_ADMIN_EMAIL).
--   저장소에 실제 이메일을 쓰지 않는다. 소문자로 저장, 이름 '관리자', ACTIVE, 비밀번호 없음(password_credentials는 US-33).
-- 탈퇴는 즉시 삭제(D-060)지만 FK에 CASCADE를 쓰지 않는다 — 지우는 순서는 서비스가 정한다 (US-37).
-- ============================================================

-- ============================================================
-- 1. 회원 (07 11-2)
-- ============================================================
CREATE TABLE users (
    `user_id`                   BIGINT          NOT NULL AUTO_INCREMENT COMMENT '회원번호',
    `email`                     VARCHAR(254)    CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL
                                                COMMENT '이메일 (소문자). 휴면이면 NULL(dormant_profiles로 옮김). bin: 악센트를 같게 보지 않게',
    `name`                      VARCHAR(50)     NULL                    COMMENT '이름 (휴면이면 NULL)',
    `status`                    VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE'
                                                COMMENT '상태: ACTIVE / DORMANT 휴면 / SUSPENDED 운영자 정지'
                                                CHECK (`status` IN ('ACTIVE', 'DORMANT', 'SUSPENDED')),
    `last_login_at`             DATETIME        NULL                    COMMENT '마지막 로그인 (휴면 판단 기준 1년)',
    `dormant_notice_sent_at`    DATETIME        NULL                    COMMENT '휴면 30일 전 안내 메일 보낸 시각',
    `dormant_at`                DATETIME        NULL                    COMMENT '휴면 된 시각 (4년 뒤 삭제 기준)',
    `created_at`                DATETIME        NOT NULL DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`                DATETIME        NOT NULL DEFAULT NOW()  COMMENT '수정일시',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email),
    INDEX idx_users_status_last_login (status, last_login_at),
    CONSTRAINT ck_users_email_lower CHECK (`email` = LOWER(`email`))
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '회원';

-- 기존 데이터를 옮길 admin 계정 (07 11-5)
INSERT INTO users (email, name, status) VALUES (LOWER(TRIM('${admin_email}')), '관리자', 'ACTIVE');

-- ============================================================
-- 2. 카테고리: 사용자별 '미지정' 1개, 이름 중복 금지도 내 카테고리끼리 (07 11-3)
-- ============================================================
ALTER TABLE categories ADD COLUMN `user_id` BIGINT NULL COMMENT '회원번호' AFTER `category_id`;
UPDATE categories SET user_id = (SELECT user_id FROM users);
ALTER TABLE categories
    MODIFY `user_id` BIGINT NOT NULL COMMENT '회원번호',
    DROP INDEX uk_categories_default_guard,
    DROP INDEX uk_categories_active_name,
    ADD UNIQUE KEY uk_categories_default_guard (user_id, default_guard),
    ADD UNIQUE KEY uk_categories_active_name (user_id, active_name),
    ADD CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (user_id);

-- ============================================================
-- 3. 일정 (calendar_details는 일정과 1:1이라 그대로)
-- ============================================================
ALTER TABLE calendars ADD COLUMN `user_id` BIGINT NULL COMMENT '회원번호' AFTER `calendar_id`;
UPDATE calendars SET user_id = (SELECT user_id FROM users);
ALTER TABLE calendars
    MODIFY `user_id` BIGINT NOT NULL COMMENT '회원번호',
    DROP INDEX idx_deleted_date_range,
    ADD INDEX idx_calendars_user_date_range (user_id, deleted, start_date_time, end_date_time),
    ADD CONSTRAINT fk_calendars_user FOREIGN KEY (user_id) REFERENCES users (user_id);

-- ============================================================
-- 4. Todo
-- ============================================================
ALTER TABLE todos ADD COLUMN `user_id` BIGINT NULL COMMENT '회원번호' AFTER `todo_id`;
UPDATE todos SET user_id = (SELECT user_id FROM users);
ALTER TABLE todos
    MODIFY `user_id` BIGINT NOT NULL COMMENT '회원번호',
    DROP INDEX idx_todos_date_range,
    DROP INDEX idx_todos_completed_at,
    ADD INDEX idx_todos_user_date_range (user_id, deleted, start_date, end_date),
    ADD INDEX idx_todos_user_completed_at (user_id, deleted, completed_at),
    ADD CONSTRAINT fk_todos_user FOREIGN KEY (user_id) REFERENCES users (user_id);

-- ============================================================
-- 5. D-Day
-- ============================================================
ALTER TABLE ddays ADD COLUMN `user_id` BIGINT NULL COMMENT '회원번호' AFTER `dday_id`;
UPDATE ddays SET user_id = (SELECT user_id FROM users);
ALTER TABLE ddays
    MODIFY `user_id` BIGINT NOT NULL COMMENT '회원번호',
    DROP INDEX idx_ddays_target_date,
    ADD INDEX idx_ddays_user_target_date (user_id, deleted, target_date),
    ADD CONSTRAINT fk_ddays_user FOREIGN KEY (user_id) REFERENCES users (user_id);

-- ============================================================
-- 6. 일기: 날짜당 1개도 사용자별
-- ============================================================
ALTER TABLE diaries ADD COLUMN `user_id` BIGINT NULL COMMENT '회원번호' AFTER `diary_id`;
UPDATE diaries SET user_id = (SELECT user_id FROM users);
ALTER TABLE diaries
    MODIFY `user_id` BIGINT NOT NULL COMMENT '회원번호',
    DROP INDEX uk_diaries_active_date,
    DROP INDEX idx_diaries_date,
    ADD UNIQUE KEY uk_diaries_active_date (user_id, active_date),
    ADD INDEX idx_diaries_user_date (user_id, deleted, diary_date),
    ADD CONSTRAINT fk_diaries_user FOREIGN KEY (user_id) REFERENCES users (user_id);

-- ============================================================
-- 7. 메모
-- ============================================================
ALTER TABLE memos ADD COLUMN `user_id` BIGINT NULL COMMENT '회원번호' AFTER `memo_id`;
UPDATE memos SET user_id = (SELECT user_id FROM users);
ALTER TABLE memos
    MODIFY `user_id` BIGINT NOT NULL COMMENT '회원번호',
    DROP INDEX idx_memos_updated_at,
    ADD INDEX idx_memos_user_updated_at (user_id, deleted, updated_at),
    ADD CONSTRAINT fk_memos_user FOREIGN KEY (user_id) REFERENCES users (user_id);

-- ============================================================
-- 8. 설정·사이드바: setting_id(1 고정) → user_id가 기본 키
-- ============================================================
ALTER TABLE sidebar_items DROP FOREIGN KEY fk_sidebar_items_setting;

ALTER TABLE user_settings
    DROP CHECK ck_user_settings_single,
    ADD COLUMN `user_id` BIGINT NULL COMMENT '회원번호' FIRST;
UPDATE user_settings SET user_id = (SELECT user_id FROM users);
ALTER TABLE user_settings
    DROP PRIMARY KEY,
    DROP COLUMN setting_id,
    MODIFY `user_id` BIGINT NOT NULL COMMENT '회원번호',
    ADD PRIMARY KEY (user_id),
    ADD CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users (user_id);

ALTER TABLE sidebar_items ADD COLUMN `user_id` BIGINT NULL COMMENT '회원번호' FIRST;
UPDATE sidebar_items SET user_id = (SELECT user_id FROM users);
ALTER TABLE sidebar_items
    DROP PRIMARY KEY,
    DROP COLUMN setting_id,
    MODIFY `user_id` BIGINT NOT NULL COMMENT '회원번호',
    ADD PRIMARY KEY (user_id, item_type),
    ADD CONSTRAINT fk_sidebar_items_settings FOREIGN KEY (user_id) REFERENCES user_settings (user_id);
