-- ============================================================
-- V5: 비밀번호 재설정 (US-35) — 메일 링크 토큰
-- 설계 문서: j-planner-product/07-db-design.md 11-2 (auth_tokens)
-- ============================================================

-- 메일 링크 토큰: 비밀번호 재설정(PASSWORD_RESET, 30분, US-35), 이메일 변경(EMAIL_CHANGE, US-37).
-- 원문은 메일에만, 여기는 SHA-256. 1회용(used_at), 같은 목적으로 새로 발급하면 이전 것은 used_at을 채워 무효.
-- 가입 인증 토큰은 pending_signups에 있다 (V4).
CREATE TABLE auth_tokens (
    `token_id`          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '토큰 번호',
    `user_id`           BIGINT          NOT NULL                COMMENT '회원번호',
    `purpose`           VARCHAR(20)     NOT NULL                COMMENT 'PASSWORD_RESET / EMAIL_CHANGE',
    `token_hash`        BINARY(32)      NOT NULL                COMMENT '토큰(32바이트 무작위)의 SHA-256',
    `new_email`         VARCHAR(254)    COLLATE utf8mb4_bin NULL COMMENT '이메일 변경일 때 바꿀 주소 (US-37)',
    `expires_at`        DATETIME        NOT NULL                COMMENT '만료 시각 (재설정 = 발급 + 30분)',
    `used_at`           DATETIME        NULL                    COMMENT '사용·무효 처리 시각 (NULL = 쓸 수 있음)',
    `created_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '발급 시각',
    PRIMARY KEY (token_id),
    CONSTRAINT uk_auth_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_auth_tokens_purpose CHECK (`purpose` IN ('PASSWORD_RESET', 'EMAIL_CHANGE')),
    CONSTRAINT fk_auth_tokens_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    INDEX idx_auth_tokens_user_purpose (user_id, purpose),
    INDEX idx_auth_tokens_expires (expires_at)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '메일 링크 토큰 (재설정·이메일 변경)';
