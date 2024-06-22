DROP TABLE IF EXISTS `CALENDARS`;

CREATE TABLE `CALENDARS` (
  `CALENDAR_ID`     BIGINT        NOT NULL  AUTO INCREMENT                                COMMENT '일정번호',
  `TITLE`           VARCHAR(255)  NOT NULL                                                COMMENT '제목',
  `IS_ALL_DAY`      VARCHAR(1)    NOT NULL  DEFAULT 'N'   CHECK (IS_ALL_DAY IN ('N','Y')) COMMENT '종일여부',
  `START_DATE_TIME` DATETIME(6)   NOT NULL                                                COMMENT '시작일시',
  `END_DATE_TIME`   DATETIME(6)   NOT NULL                                                COMMENT '종료일시',
  `INFORMATION`     VARCHAR(255)  NULL                                                    COMMENT '내용',
  `LATITUDE`        DOUBLE        NULL                                                    COMMENT '위도',
  `LONGITUDE`       DOUBLE        NULL                                                    COMMENT '경도',
  `CREATED_AT`      DATETIME(6)   NOT NULL  DEFAULT NOW()                                 COMMENT '생성일시',
  `UPDATED_AT`      DATETIME(6)   NOT NULL  DEFAULT NOW()                                 COMMENT '수정일시',
  `IS_DELETED`      VARCHAR(1)    NOT NULL  DEFAULT 'N'   CHECK (IS_DELETED IN ('N','Y')) COMMENT '삭제여부',
  `DELETED_AT`      DATETIME(6)   NULL                                                    COMMENT '삭제일시'
) COMMENT='일정 정보 테이블';

ALTER TABLE `CALENDARS` ADD CONSTRAINT `PK_CALENDARS` PRIMARY KEY (
  `CALENDAR_ID`
);
