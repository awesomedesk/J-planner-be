-- ============================================================
-- V4: 이메일 가입 (US-33) — 임시 가입 (D-061)
-- 설계 문서: j-planner-product/07-db-design.md 11-2 (pending_signups)
-- ============================================================

-- 임시 가입: 인증 링크를 누르기 전까지만 있다. 로그인·서비스 사용 불가.
-- 인증하면 한 트랜잭션에서 users + password_credentials + 기본 데이터를 만들고 이 행을 지운다.
-- 같은 이메일로 다시 가입하면 이 행을 새 값으로 바꾼다 (이전 링크 무효). 만료된 행은 매시간 정리 작업이 지운다.
CREATE TABLE pending_signups (
    `pending_id`        BIGINT          NOT NULL AUTO_INCREMENT COMMENT '임시 가입 번호',
    `email`             VARCHAR(254)    COLLATE utf8mb4_bin NOT NULL COMMENT '이메일 (소문자). users.email과 같은 규칙',
    `name`              VARCHAR(50)     NOT NULL                COMMENT '이름 (1~50자)',
    `password_hash`     VARCHAR(255)    NOT NULL                COMMENT 'Argon2id 인코딩 문자열. 원문은 저장하지 않음',
    `token_hash`        BINARY(32)      NOT NULL                COMMENT '인증 링크 토큰(32바이트 무작위)의 SHA-256. 원문은 메일에만',
    `expires_at`        DATETIME        NOT NULL                COMMENT '만든 시각 + 24시간. 이 뒤로는 인증 불가·정리 대상',
    `last_sent_at`      DATETIME        NOT NULL                COMMENT '인증 메일을 마지막으로 보낸 시각 (다시 보내기 60초 간격)',
    `created_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '임시 가입 시각 (다시 가입하면 새 시각)',
    PRIMARY KEY (pending_id),
    CONSTRAINT uk_pending_signups_email UNIQUE (email),
    CONSTRAINT uk_pending_signups_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_pending_signups_email_lower CHECK (email = LOWER(email)),
    INDEX idx_pending_signups_expires (expires_at)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '임시 가입 (24시간, D-061)';
