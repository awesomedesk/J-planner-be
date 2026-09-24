-- ============================================================
-- J-planner MVP 스키마 (새로 설치용)
-- 설계 문서: j-planner-product/07-db-design.md
-- 대상: MySQL 8.4
-- 작성: 2026-09-24
--
-- 공통 규칙 (기존 calendars 테이블과 동일)
--   - PK: BIGINT AUTO_INCREMENT, 이름은 <단수형>_id
--   - Y/N 값: VARCHAR(1) + CHECK (JPA BooleanToStringConverter)
--   - 코드 값: VARCHAR + CHECK (JPA @Enumerated(EnumType.STRING))
--   - 감사 컬럼: created_at, updated_at, deleted, deleted_at (BaseEntity)
--   - 삭제는 soft delete (deleted = 'Y')
--   - user_id 없음 (D-003: MVP는 1인 사용)
-- 기존 DB를 올릴 때는 migration-20260924-mvp.sql 을 사용한다.
-- ============================================================

CREATE DATABASE IF NOT EXISTS jp;
USE jp;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `sidebar_items`;
DROP TABLE IF EXISTS `user_settings`;
DROP TABLE IF EXISTS `memos`;
DROP TABLE IF EXISTS `diaries`;
DROP TABLE IF EXISTS `ddays`;
DROP TABLE IF EXISTS `todos`;
DROP TABLE IF EXISTS `calendar_details`;
DROP TABLE IF EXISTS `calendars`;
DROP TABLE IF EXISTS `categories`;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 1. 카테고리 (CAT-01~04, D-010, D-014)
-- ============================================================
CREATE TABLE categories (
    `category_id`       BIGINT          NOT NULL AUTO_INCREMENT COMMENT '카테고리번호',
    `name`              VARCHAR(50)     NOT NULL                COMMENT '이름',
    `color`             VARCHAR(7)      NOT NULL                COMMENT '색 (#RRGGBB)',
    `is_default`        VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '기본 카테고리(미지정) 여부'
                                        CHECK (`is_default` IN ('N', 'Y')),
    `sort_order`        INT             NOT NULL DEFAULT 0      COMMENT '표시 순서 (미지정은 항상 맨 위, D-029)',
    `created_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '수정일시',
    `deleted`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부'
                                        CHECK (`deleted` IN ('N', 'Y')),
    `deleted_at`        DATETIME        NULL                    COMMENT '삭제일시',
    -- 기본 카테고리는 1개만 (삭제 안 된 것 중). NULL은 UNIQUE에 걸리지 않는 점을 이용
    `default_guard`     TINYINT         GENERATED ALWAYS AS
                                        (IF(`is_default` = 'Y' AND `deleted` = 'N', 1, NULL)) STORED
                                        COMMENT '기본 카테고리 중복 방지용 (JPA 매핑 안 함)',
    -- 삭제 안 된 카테고리끼리 이름 중복 금지 (D-029)
    `active_name`       VARCHAR(50)     GENERATED ALWAYS AS
                                        (IF(`deleted` = 'N', `name`, NULL)) STORED
                                        COMMENT '이름 중복 방지용 (JPA 매핑 안 함)',
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_categories_default_guard (default_guard),
    UNIQUE KEY uk_categories_active_name (active_name),
    CHECK (`color` REGEXP '^#[0-9A-Fa-f]{6}$')
) COMMENT '카테고리 (일정·Todo 공용)';

-- ============================================================
-- 2. 일정 (SCH, 기존 테이블 + category_id)
-- ============================================================
CREATE TABLE calendars (
    `calendar_id`       BIGINT          NOT NULL AUTO_INCREMENT COMMENT '일정번호',
    `category_id`       BIGINT          NOT NULL                COMMENT '카테고리번호 (기본: 미지정)',
    `title`             VARCHAR(255)    NOT NULL                COMMENT '제목',
    `all_day`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '종일여부'
                                        CHECK (`all_day` IN ('N', 'Y')),
    `start_date_time`   DATETIME        NOT NULL                COMMENT '시작일시',
    `end_date_time`     DATETIME        NOT NULL                COMMENT '종료일시',
    `color`             VARCHAR(8)      NULL                    COMMENT '일정 색 (NULL = 테마의 Theme2 색, D-030)',
    `created_at`        DATETIME        NULL     DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`        DATETIME        NULL     DEFAULT NOW()  COMMENT '수정일시',
    `deleted`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부'
                                        CHECK (`deleted` IN ('N', 'Y')),
    `deleted_at`        DATETIME        NULL                    COMMENT '삭제일시',
    PRIMARY KEY (calendar_id),
    INDEX idx_deleted_date_range (deleted, start_date_time, end_date_time),
    CONSTRAINT fk_calendars_category FOREIGN KEY (category_id) REFERENCES categories (category_id)
) COMMENT '일정';

CREATE TABLE calendar_details (
    `calendar_id`       BIGINT          NOT NULL                COMMENT '일정번호',
    `description`       TEXT            NULL                    COMMENT '일정설명(메모)',
    `text_location`     TEXT            NULL                    COMMENT '위치명',
    `location`          POINT           NULL                    COMMENT '위치',
    `url`               VARCHAR(2048)   NULL                    COMMENT 'URL (SCH-13)',
    `created_at`        DATETIME        NULL     DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`        DATETIME        NULL     DEFAULT NOW()  COMMENT '수정일시',
    `deleted`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부'
                                        CHECK (`deleted` IN ('N', 'Y')),
    `deleted_at`        DATETIME        NULL                    COMMENT '삭제일시',
    PRIMARY KEY (calendar_id)
) COMMENT '일정 상세 (calendars와 1:1)';

-- ============================================================
-- 3. Todo (TODO-01~05, 09, 10, 12~14, D-007, D-013, D-015, D-027)
-- ============================================================
CREATE TABLE todos (
    `todo_id`           BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'Todo번호',
    `category_id`       BIGINT          NOT NULL                COMMENT '카테고리번호 (기본: 미지정)',
    `title`             VARCHAR(255)    NOT NULL                COMMENT '제목',
    `todo_type`         VARCHAR(10)     NOT NULL DEFAULT 'DAY'  COMMENT '종류: DAY 하루 / PERIOD 기간 / WEEK 주간 목표 / MONTH 월간 목표'
                                        CHECK (`todo_type` IN ('DAY', 'PERIOD', 'WEEK', 'MONTH')),
    `start_date`        DATE            NOT NULL                COMMENT '시작일 (DAY는 그날, WEEK는 주 첫날, MONTH는 1일)',
    `end_date`          DATE            NOT NULL                COMMENT '마감일 (DAY는 그날, WEEK는 주 마지막날, MONTH는 말일)',
    `start_time`        TIME            NULL                    COMMENT '시간표 시작 시각 (NULL = 시간 지정 안 함)',
    `duration_minutes`  SMALLINT        NULL                    COMMENT '시간표 길이(분). 종료 = 시작 + 길이',
    `color`             VARCHAR(7)      NULL                    COMMENT 'Todo 색 (NULL = 테마의 Theme2 색, D-030)',
    `completed`         VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '완료여부 (기간 Todo도 한 번 체크 = 전체 완료, D-027)'
                                        CHECK (`completed` IN ('N', 'Y')),
    `completed_at`      DATETIME        NULL                    COMMENT '완료일시 (DIARY-04: 그날 완료한 Todo)',
    `sort_order`        INT             NOT NULL DEFAULT 0      COMMENT '박스 표시 순서 (TODO-09). 새 Todo = 최댓값 + 1 (맨 아래, D-029)',
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

-- ============================================================
-- 4. D-Day (DDAY-01~05, D-012, D-020)
-- ============================================================
CREATE TABLE ddays (
    `dday_id`           BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'D-Day번호',
    `title`             VARCHAR(255)    NOT NULL                COMMENT '제목',
    `target_date`       DATE            NOT NULL                COMMENT '목표 날짜 (달력에 항상 표시)',
    `count_type`        VARCHAR(10)     NOT NULL DEFAULT 'COUNTDOWN'
                                        COMMENT '계산 기준: COUNTDOWN 당일=0일(남은 날) / COUNTUP 당일=1일(지난 날)'
                                        CHECK (`count_type` IN ('COUNTDOWN', 'COUNTUP')),
    `show_interval`     VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '표시옵션: N일 단위'
                                        CHECK (`show_interval` IN ('N', 'Y')),
    `interval_days`     INT             NOT NULL DEFAULT 100    COMMENT 'N일 단위의 N'
                                        CHECK (`interval_days` >= 1),
    `show_last_days`    VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '표시옵션: 마지막 N일은 매일 (COUNTDOWN만)'
                                        CHECK (`show_last_days` IN ('N', 'Y')),
    `last_days`         INT             NOT NULL DEFAULT 7      COMMENT '마지막 N일의 N'
                                        CHECK (`last_days` >= 1),
    `show_daily`        VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '표시옵션: 매일, 등록일(created_at)부터 (COUNTDOWN만)'
                                        CHECK (`show_daily` IN ('N', 'Y')),
    `show_yearly`       VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '표시옵션: 매년 n주년 (COUNTUP만)'
                                        CHECK (`show_yearly` IN ('N', 'Y')),
    `sort_order`        INT             NOT NULL DEFAULT 0      COMMENT '목록 표시 순서 (사용자 순서, 새 D-Day = 최댓값 + 1, D-029)',
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

-- ============================================================
-- 5. 일기 (DIARY-01~03, D-011: 날짜당 1개)
-- ============================================================
CREATE TABLE diaries (
    `diary_id`          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '일기번호',
    `diary_date`        DATE            NOT NULL                COMMENT '날짜',
    `content`           TEXT            NOT NULL                COMMENT '내용',
    `created_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '수정일시',
    `deleted`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부'
                                        CHECK (`deleted` IN ('N', 'Y')),
    `deleted_at`        DATETIME        NULL                    COMMENT '삭제일시',
    -- 삭제 안 된 일기는 날짜당 1개. 삭제한 날짜에는 다시 쓸 수 있음
    `active_date`       DATE            GENERATED ALWAYS AS
                                        (IF(`deleted` = 'N', `diary_date`, NULL)) STORED
                                        COMMENT '날짜당 1개 보장용 (JPA 매핑 안 함)',
    PRIMARY KEY (diary_id),
    UNIQUE KEY uk_diaries_active_date (active_date),
    INDEX idx_diaries_date (deleted, diary_date)
) COMMENT '일기';

-- ============================================================
-- 6. 메모 (MEMO-01~03, D-011: 날짜 없음)
-- ============================================================
CREATE TABLE memos (
    `memo_id`           BIGINT          NOT NULL AUTO_INCREMENT COMMENT '메모번호',
    `title`             VARCHAR(255)    NULL                    COMMENT '제목',
    `content`           TEXT            NULL                    COMMENT '내용',
    `created_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '수정일시',
    `deleted`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부'
                                        CHECK (`deleted` IN ('N', 'Y')),
    `deleted_at`        DATETIME        NULL                    COMMENT '삭제일시',
    PRIMARY KEY (memo_id),
    INDEX idx_memos_updated_at (deleted, updated_at)
) COMMENT '메모';

-- ============================================================
-- 7. 사용자 설정 (SET-01~05, CAL-04, THEME, SIDE-01, D-021, D-024)
--    MVP는 1인 사용이라 1행만 둔다 (setting_id = 1 고정)
-- ============================================================
CREATE TABLE user_settings (
    `setting_id`            BIGINT      NOT NULL                COMMENT '설정번호 (MVP는 1 고정. 로그인 도입 시 user_id로 대체)',
    `week_start_day`        VARCHAR(3)  NOT NULL DEFAULT 'SUN'  COMMENT '주 시작 요일'
                                        CHECK (`week_start_day` IN ('SUN', 'MON')),
    `start_view`            VARCHAR(10) NOT NULL DEFAULT 'MONTH' COMMENT '처음 화면: MONTH / WEEK / DAY / LAST(마지막에 본 화면 = 브라우저 localStorage에 저장, D-029)'
                                        CHECK (`start_view` IN ('MONTH', 'WEEK', 'DAY', 'LAST')),
    `time_format`           VARCHAR(3)  NOT NULL DEFAULT '24H'  COMMENT '시간 표시'
                                        CHECK (`time_format` IN ('24H', '12H')),
    `timetable_start_hour`  TINYINT     NOT NULL DEFAULT 6      COMMENT '시간표 시작 시 (0~23)',
    `timetable_end_hour`    TINYINT     NOT NULL DEFAULT 24     COMMENT '시간표 끝 시 (1~24)',
    `slot_minutes`          TINYINT     NOT NULL DEFAULT 60     COMMENT '시간표 칸 간격(분)'
                                        CHECK (`slot_minutes` IN (30, 60)),
    `dark_mode`             VARCHAR(1)  NOT NULL DEFAULT 'N'    COMMENT '다크 모드'
                                        CHECK (`dark_mode` IN ('N', 'Y')),
    `color_theme`           VARCHAR(10) NOT NULL DEFAULT 'GREEN' COMMENT '색 테마'
                                        CHECK (`color_theme` IN ('GREEN', 'BROWN', 'GRAY')),
    `sidebar_open`          VARCHAR(1)  NOT NULL DEFAULT 'Y'    COMMENT '사이드바 열림 상태 기억 (D-021)'
                                        CHECK (`sidebar_open` IN ('N', 'Y')),
    `created_at`            DATETIME    NOT NULL DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`            DATETIME    NOT NULL DEFAULT NOW()  COMMENT '수정일시',
    PRIMARY KEY (setting_id),
    CONSTRAINT ck_user_settings_single    CHECK (`setting_id` = 1),
    CONSTRAINT ck_user_settings_timetable CHECK (`timetable_start_hour` BETWEEN 0 AND 23
                                                 AND `timetable_end_hour` BETWEEN 1 AND 24
                                                 AND `timetable_start_hour` < `timetable_end_hour`)
) COMMENT '사용자 설정';

-- 사이드바·날짜 시트 항목 표시/순서 (SIDE-02, 03)
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

-- ============================================================
-- 기본 데이터 (필수)
-- ============================================================
INSERT INTO categories (name, color, is_default, sort_order)
VALUES ('미지정', '#6B6B6B', 'Y', 0);

INSERT INTO user_settings (setting_id) VALUES (1);

INSERT INTO sidebar_items (setting_id, item_type, visible, sort_order)
VALUES (1, 'TODO',  'Y', 0),
       (1, 'DDAY',  'Y', 1),
       (1, 'DIARY', 'Y', 2),
       (1, 'MEMO',  'Y', 3);
