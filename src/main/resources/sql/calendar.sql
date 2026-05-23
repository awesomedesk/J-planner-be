CREATE DATABASE jp;
USE jp;

-- ============================================
-- Calendar Table
-- ============================================
DROP TABLE IF EXISTS `calendars`;
CREATE TABLE calendars (
    `calendar_id`       BIGINT          NOT NULL AUTO_INCREMENT COMMENT '일정번호',
    `title`             VARCHAR(255)    NOT NULL                COMMENT '제목',
    `all_day`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '종일여부'
                                        CHECK (`all_day` IN ('N', 'Y')),
    `start_date_time`   DATETIME        NOT NULL                COMMENT '시작일시',
    `end_date_time`     DATETIME        NOT NULL                COMMENT '종료일시',
    `color`             VARCHAR(8)      NULL                    COMMENT '색',
    `created_at`        DATETIME        NULL     DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`        DATETIME        NULL     DEFAULT NOW()  COMMENT '수정일시',
    `deleted`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부'
                                        CHECK (`deleted` IN ('N', 'Y')),
    `deleted_at`        DATETIME        NULL                    COMMENT '삭제일시',
    PRIMARY KEY (calendar_id),
    INDEX idx_deleted_date_range (deleted, start_date_time, end_date_time)
);

-- ============================================
-- Calendar Details Table
-- ============================================
DROP TABLE IF EXISTS `calendar_details`;
CREATE TABLE calendar_details (
    `calendar_id`       BIGINT          NOT NULL                COMMENT '일정번호',
    `description`       TEXT            NULL                    COMMENT '일정설명',
    `text_location`     TEXT            NULL                    COMMENT '위치명',
    `location`          POINT           NULL                    COMMENT '위치',
    `created_at`        DATETIME        NULL     DEFAULT NOW()  COMMENT '생성일시',
    `updated_at`        DATETIME        NULL     DEFAULT NOW()  COMMENT '수정일시',
    `deleted`           VARCHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부'
                                        CHECK (`deleted` IN ('N', 'Y')),
    `deleted_at`        DATETIME        NULL                    COMMENT '삭제일시',
    PRIMARY KEY (calendar_id)
);

-- ============================================
-- Sample Data - Calendars
-- ============================================
INSERT INTO calendars (title, all_day, start_date_time, end_date_time, color)
VALUES
    ('팀 미팅',           'N', '2026-05-15 10:00:00', '2026-05-15 12:00:00', '#FF5733'),
    ('휴가',              'Y', '2026-05-20 00:00:00', '2026-05-23 23:59:59', '#28A745'),
    ('헬스장 운동',       'N', '2026-05-16 18:00:00', '2026-05-16 19:00:00', '#FFC107'),
    ('병원 진료',         'N', '2026-05-17 14:30:00', '2026-05-17 15:00:00', '#DC3545'),
    ('온라인 강의 수강',  'N', '2026-05-18 19:00:00', '2026-05-18 22:00:00', '#007BFF'),
    ('친구와 점심',       'N', '2026-05-19 12:00:00', '2026-05-19 13:00:00', '#FD7E14'),
    ('프로젝트 마감',     'N', '2026-05-21 09:00:00', '2026-05-21 13:00:00', '#6F42C1'),
    ('영화 관람',         'N', '2026-05-22 20:00:00', '2026-05-22 22:00:00', '#E83E8C'),
    ('마트 장보기',       'N', '2026-05-23 15:00:00', '2026-05-23 16:00:00', '#17A2B8'),
    ('부산 출장',         'Y', '2026-05-25 00:00:00', '2026-05-25 23:59:59', '#6C757D'),
    ('새벽작업',         'N', '2026-05-26 23:00:00', '2026-05-27 04:59:59', '#AAAAAA'),
    ('패키지여행',            'Y', '2026-05-20 00:00:00', '2026-05-22 23:59:59', '#20A045'),
    ('알파 호텔',            'Y', '2026-05-20 00:00:00', '2026-05-21 23:59:59', '#790709'),
    ('베타 호텔',            'Y', '2026-05-21 00:00:00', '2026-05-22 23:59:59', '#790A09');

-- ============================================
-- Sample Data - Calendar Details
-- ============================================
INSERT INTO calendar_details (calendar_id, description, text_location, location)
VALUES
    (1,  '주간 팀 미팅 - 프로젝트 진행 상황 공유',  '회의실 A',              POINT(126.9780, 37.5665)),
    (2,  '연차 휴가 - 개인 시간',                 '집',                    POINT(127.0000, 37.5000)),
    (3,  '주 3회 운동 루틴 - 상체 운동',          '피트니스 센터',         POINT(126.9895, 37.5651)),
    (4,  '정기 건강검진',                         '서울대학교병원',        POINT(126.9422, 37.5796)),
    (5,  'Spring Boot 심화 과정',                 'localhost:3000/calendar', NULL),
    (6,  '대학 동기 모임',                        '강남역 맛집',           POINT(127.0276, 37.4979)),
    (7,  '분기별 프로젝트 최종 마감',             '사무실',                POINT(127.0473, 37.5172)),
    (8,  '어벤져스 신작 관람',                    'CGV 강남점',            POINT(127.0258, 37.5009)),
    (9,  '주간 장보기 - 생필품 구매',             '이마트 본점',           POINT(127.0536, 37.5060)),
    (10, '클라이언트 미팅 및 계약 체결',          '부산 해운대구',         POINT(129.1600, 35.1595)),
    (11,  '이벤트 시작 모니터링',                 '집',                    POINT(127.0000, 37.5000)),
    (12,  '가야해 패키지',                 '부산 해운대구',         POINT(129.1600, 35.1595)),
    (13,  NULL,                 '부산 해운대구',         POINT(129.1600, 35.1595)),
    (14,  NULL,                 '부산 해운대구',         POINT(129.1600, 35.1595));

-- ============================================
-- Verification Queries
-- ============================================
SELECT * FROM jp.calendars;
SELECT * FROM jp.calendar_details;
