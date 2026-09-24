-- ============================================================
-- J-planner 마이그레이션: 기존 DB(calendar.sql) → MVP 스키마
-- 설계 문서: j-planner-product/07-db-design.md
-- 대상: MySQL 8.4 / 작성: 2026-09-24
--
-- 순서
--   1) 새 테이블 생성 (schema.sql의 categories, todos, ddays, diaries, memos,
--      user_settings, sidebar_items 와 같은 정의)
--   2) 기본 데이터(미지정 카테고리, 설정 1행, 사이드바 항목 4개) 넣기
--   3) calendars 에 category_id 추가 → 기존 일정을 '미지정'으로 연결 → NOT NULL + FK
--   4) calendar_details 에 url 추가
-- 실행 전 백업: mysqldump jp > jp_backup_20260924.sql
-- ============================================================

USE jp;

-- ------------------------------------------------------------
-- 1) 새 테이블
-- ------------------------------------------------------------
CREATE TABLE categories (
    `category_id`       BIGINT          NOT NULL AUTO_INCREMENT COMMENT '카테고리번호',
    `name`              VARCHAR(50)     NOT NULL                COMMENT '이름',
    `color`             VARCHAR(7)      NOT NULL                COMMENT '색 (#RRGGBB)',
    `is_default`        VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '기본 카테고리(미지정) 여부'
                                        CHECK (`is_default` IN ('N', 'Y')),
    `created_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '수정일시',
    `deleted`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부'
                                        CHECK (`deleted` IN ('N', 'Y')),
    `deleted_at`        DATETIME        NULL                    COMMENT '삭제일시',
    `default_guard`     TINYINT         GENERATED ALWAYS AS
                                        (IF(`is_default` = 'Y' AND `deleted` = 'N', 1, NULL)) STORED
                                        COMMENT '기본 카테고리 중복 방지용 (JPA 매핑 안 함)',
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_categories_default_guard (default_guard),
    CHECK (`color` REGEXP '^#[0-9A-Fa-f]{6}$')
) COMMENT '카테고리 (일정·Todo 공용)';

CREATE TABLE todos (
    `todo_id`           BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'Todo번호',
    `category_id`       BIGINT          NOT NULL                COMMENT '카테고리번호 (기본: 미지정)',
    `title`             VARCHAR(255)    NOT NULL                COMMENT '제목',
    `todo_type`         VARCHAR(10)     NOT NULL DEFAULT 'DAY'  COMMENT '종류: DAY 하루 / PERIOD 기간 / WEEK 주간 목표 / MONTH 월간 목표'
                                        CHECK (`todo_type` IN ('DAY', 'PERIOD', 'WEEK', 'MONTH')),
    `start_date`        DATE            NOT NULL                COMMENT '시작일',
    `end_date`          DATE            NOT NULL                COMMENT '마감일',
    `start_time`        TIME            NULL                    COMMENT '시간표 시작 시각 (NULL = 시간 지정 안 함)',
    `duration_minutes`  SMALLINT        NULL                    COMMENT '시간표 길이(분)',
    `color`             VARCHAR(7)      NULL                    COMMENT 'Todo 색 (NULL = 카테고리 색 기준)',
    `completed`         VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '완료여부'
                                        CHECK (`completed` IN ('N', 'Y')),
    `completed_at`      DATETIME        NULL                    COMMENT '완료일시',
    `sort_order`        INT             NOT NULL DEFAULT 0      COMMENT '박스 표시 순서',
    `created_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '수정일시',
    `deleted`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부'
                                        CHECK (`deleted` IN ('N', 'Y')),
    `deleted_at`        DATETIME        NULL                    COMMENT '삭제일시',
    PRIMARY KEY (todo_id),
    INDEX idx_todos_date_range (deleted, start_date, end_date),
    INDEX idx_todos_completed_at (deleted, completed_at),
    CONSTRAINT fk_todos_category FOREIGN KEY (category_id) REFERENCES categories (category_id),
    CONSTRAINT ck_todos_date_order   CHECK (`start_date` <= `end_date`),
    CONSTRAINT ck_todos_day_one_date CHECK (`todo_type` <> 'DAY' OR `start_date` = `end_date`),
    CONSTRAINT ck_todos_time_pair    CHECK ((`start_time` IS NULL) = (`duration_minutes` IS NULL)),
    CONSTRAINT ck_todos_duration     CHECK (`duration_minutes` IS NULL OR `duration_minutes` BETWEEN 1 AND 1440),
    CONSTRAINT ck_todos_completed_at CHECK ((`completed` = 'Y') = (`completed_at` IS NOT NULL))
) COMMENT 'Todo';

CREATE TABLE ddays (
    `dday_id`           BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'D-Day번호',
    `title`             VARCHAR(255)    NOT NULL                COMMENT '제목',
    `target_date`       DATE            NOT NULL                COMMENT '목표 날짜',
    `count_type`        VARCHAR(10)     NOT NULL DEFAULT 'COUNTDOWN'
                                        COMMENT '계산 기준: COUNTDOWN 당일=0일 / COUNTUP 당일=1일'
                                        CHECK (`count_type` IN ('COUNTDOWN', 'COUNTUP')),
    `show_interval`     VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '표시옵션: N일 단위'
                                        CHECK (`show_interval` IN ('N', 'Y')),
    `interval_days`     INT             NOT NULL DEFAULT 100    COMMENT 'N일 단위의 N'
                                        CHECK (`interval_days` >= 1),
    `show_last_days`    VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '표시옵션: 마지막 N일은 매일'
                                        CHECK (`show_last_days` IN ('N', 'Y')),
    `last_days`         INT             NOT NULL DEFAULT 7      COMMENT '마지막 N일의 N'
                                        CHECK (`last_days` >= 1),
    `show_daily`        VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '표시옵션: 매일(등록일부터)'
                                        CHECK (`show_daily` IN ('N', 'Y')),
    `show_yearly`       VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '표시옵션: 매년'
                                        CHECK (`show_yearly` IN ('N', 'Y')),
    `created_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '수정일시',
    `deleted`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부'
                                        CHECK (`deleted` IN ('N', 'Y')),
    `deleted_at`        DATETIME        NULL                    COMMENT '삭제일시',
    PRIMARY KEY (dday_id),
    INDEX idx_ddays_target_date (deleted, target_date),
    CONSTRAINT ck_ddays_countdown_options CHECK (`count_type` <> 'COUNTDOWN' OR `show_yearly` = 'N'),
    CONSTRAINT ck_ddays_countup_options   CHECK (`count_type` <> 'COUNTUP' OR (`show_last_days` = 'N' AND `show_daily` = 'N'))
) COMMENT 'D-Day';

CREATE TABLE diaries (
    `diary_id`          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '일기번호',
    `diary_date`        DATE            NOT NULL                COMMENT '날짜',
    `content`           TEXT            NOT NULL                COMMENT '내용',
    `created_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '수정일시',
    `deleted`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부'
                                        CHECK (`deleted` IN ('N', 'Y')),
    `deleted_at`        DATETIME        NULL                    COMMENT '삭제일시',
    `active_date`       DATE            GENERATED ALWAYS AS
                                        (IF(`deleted` = 'N', `diary_date`, NULL)) STORED
                                        COMMENT '날짜당 1개 보장용 (JPA 매핑 안 함)',
    PRIMARY KEY (diary_id),
    UNIQUE KEY uk_diaries_active_date (active_date),
    INDEX idx_diaries_date (deleted, diary_date)
) COMMENT '일기';

CREATE TABLE memos (
    `memo_id`           BIGINT          NOT NULL AUTO_INCREMENT COMMENT '메모번호',
    `title`             VARCHAR(255)    NULL                    COMMENT '제목',
    `content`           TEXT            NULL                    COMMENT '내용',
    `created_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '수정일시',
    `deleted`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부'
                                        CHECK (`deleted` IN ('N', 'Y')),
    `deleted_at`        DATETIME        NULL                    COMMENT '삭제일시',
    PRIMARY KEY (memo_id)
) COMMENT '메모';

CREATE TABLE user_settings (
    `setting_id`            BIGINT      NOT NULL                COMMENT '설정번호 (MVP는 1 고정)',
    `week_start_day`        VARCHAR(3)  NOT NULL DEFAULT 'SUN'  COMMENT '주 시작 요일'
                                        CHECK (`week_start_day` IN ('SUN', 'MON')),
    `start_view`            VARCHAR(10) NOT NULL DEFAULT 'MONTH' COMMENT '처음 화면'
                                        CHECK (`start_view` IN ('MONTH', 'WEEK', 'DAY', 'LAST')),
    `last_view`             VARCHAR(10) NULL                    COMMENT '마지막에 본 화면'
                                        CHECK (`last_view` IN ('MONTH', 'WEEK', 'THREE_DAY', 'DAY', 'LIST')),
    `time_format`           VARCHAR(3)  NOT NULL DEFAULT '24H'  COMMENT '시간 표시'
                                        CHECK (`time_format` IN ('24H', '12H')),
    `timetable_start_hour`  TINYINT     NOT NULL DEFAULT 6      COMMENT '시간표 시작 시',
    `timetable_end_hour`    TINYINT     NOT NULL DEFAULT 24     COMMENT '시간표 끝 시',
    `slot_minutes`          TINYINT     NOT NULL DEFAULT 60     COMMENT '시간표 칸 간격(분)'
                                        CHECK (`slot_minutes` IN (30, 60)),
    `dark_mode`             VARCHAR(1)  NOT NULL DEFAULT 'N'    COMMENT '다크 모드'
                                        CHECK (`dark_mode` IN ('N', 'Y')),
    `color_theme`           VARCHAR(10) NOT NULL DEFAULT 'GREEN' COMMENT '색 테마'
                                        CHECK (`color_theme` IN ('GREEN', 'BROWN', 'GRAY')),
    `sidebar_open`          VARCHAR(1)  NOT NULL DEFAULT 'Y'    COMMENT '사이드바 열림 상태'
                                        CHECK (`sidebar_open` IN ('N', 'Y')),
    `created_at`            DATETIME    NOT NULL DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`            DATETIME    NOT NULL DEFAULT NOW()  COMMENT '수정일시',
    PRIMARY KEY (setting_id),
    CONSTRAINT ck_user_settings_single    CHECK (`setting_id` = 1),
    CONSTRAINT ck_user_settings_timetable CHECK (`timetable_start_hour` BETWEEN 0 AND 23
                                                 AND `timetable_end_hour` BETWEEN 1 AND 24
                                                 AND `timetable_start_hour` < `timetable_end_hour`)
) COMMENT '사용자 설정';

CREATE TABLE sidebar_items (
    `setting_id`        BIGINT          NOT NULL                COMMENT '설정번호',
    `item_type`         VARCHAR(10)     NOT NULL                COMMENT '항목'
                                        CHECK (`item_type` IN ('TODO', 'DDAY', 'DIARY', 'MEMO')),
    `visible`           VARCHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '표시여부'
                                        CHECK (`visible` IN ('N', 'Y')),
    `sort_order`        TINYINT         NOT NULL                COMMENT '표시 순서 (0부터)',
    PRIMARY KEY (setting_id, item_type),
    CONSTRAINT fk_sidebar_items_setting FOREIGN KEY (setting_id) REFERENCES user_settings (setting_id)
) COMMENT '사이드바 항목 설정';

-- ------------------------------------------------------------
-- 2) 기본 데이터
-- ------------------------------------------------------------
INSERT INTO categories (name, color, is_default)
VALUES ('미지정', '#6B6B6B', 'Y');

INSERT INTO user_settings (setting_id) VALUES (1);

INSERT INTO sidebar_items (setting_id, item_type, visible, sort_order)
VALUES (1, 'TODO',  'Y', 0),
       (1, 'DDAY',  'Y', 1),
       (1, 'DIARY', 'Y', 2),
       (1, 'MEMO',  'Y', 3);

-- ------------------------------------------------------------
-- 3) calendars.category_id
-- ------------------------------------------------------------
ALTER TABLE calendars
    ADD COLUMN `category_id` BIGINT NULL COMMENT '카테고리번호 (기본: 미지정)' AFTER `calendar_id`;

UPDATE calendars
SET category_id = (SELECT category_id FROM categories WHERE is_default = 'Y' AND deleted = 'N');

ALTER TABLE calendars
    MODIFY COLUMN `category_id` BIGINT NOT NULL COMMENT '카테고리번호 (기본: 미지정)',
    MODIFY COLUMN `color` VARCHAR(8) NULL COMMENT '일정 색 (NULL = 카테고리 색의 연한 톤, D-019)',
    ADD CONSTRAINT fk_calendars_category FOREIGN KEY (category_id) REFERENCES categories (category_id);

-- ------------------------------------------------------------
-- 4) calendar_details.url
-- ------------------------------------------------------------
ALTER TABLE calendar_details
    ADD COLUMN `url` VARCHAR(2048) NULL COMMENT 'URL (SCH-13)' AFTER `location`;
