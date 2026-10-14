-- ============================================================
-- V3: 이메일 로그인 (US-34) — 비밀번호 해시, 보안 감사 기록, 회원 정지 열 (D-069)
-- 설계 문서: j-planner-product/07-db-design.md 11-2 (password_credentials, audit_logs)
-- ============================================================

-- 비밀번호 해시 (Argon2id m=19MiB·t=2·p=1). 구글로만 가입한 회원·admin 첫 설정 전에는 행이 없다
CREATE TABLE password_credentials (
    `user_id`           BIGINT          NOT NULL                COMMENT '회원번호',
    `password_hash`     VARCHAR(255)    NOT NULL                COMMENT 'Argon2id 인코딩 문자열 ($argon2id$...). 원문은 저장하지 않음',
    `changed_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '비밀번호를 정하거나 바꾼 시각',
    PRIMARY KEY (user_id),
    CONSTRAINT fk_password_credentials_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '비밀번호 (회원당 0~1행)';

-- 보안 감사 기록. 탈퇴 뒤에도 남기므로 user_id에 FK를 걸지 않는다. 이메일·비밀번호·토큰·본문은 넣지 않는다.
-- 앱 DB 계정(jp_app)은 INSERT·SELECT만 (07 11-6). 1년 보관 (지우는 작업은 US-41).
-- 운영자(어드민) 행동은 여기가 아니라 jpadm.admin_audit_logs (어드민 저장소 주인, D-065)
CREATE TABLE audit_logs (
    `audit_id`          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '기록번호',
    `user_id`           BIGINT          NULL                    COMMENT '회원번호 (없는 계정으로 시도하면 NULL)',
    `actor`             VARCHAR(10)     NOT NULL                COMMENT '누가: USER / SYSTEM'
                                        CHECK (`actor` IN ('USER', 'SYSTEM')),
    `event`             VARCHAR(30)     NOT NULL                COMMENT 'LOGIN, LOGIN_FAILED, RATE_LIMITED, LOGOUT, LOGOUT_OTHERS … (08 13-8)',
    `ip`                VARCHAR(45)     NULL                    COMMENT '요청 IP (IPv6 최대 45자)',
    `user_agent`        VARCHAR(255)    NULL                    COMMENT '브라우저 정보 (255자에서 자름)',
    `detail`            JSON            NULL                    COMMENT '부가 정보 (예: {"method":"PASSWORD"}). 개인정보 없음',
    `created_at`        DATETIME        NOT NULL DEFAULT NOW()  COMMENT '기록 시각',
    PRIMARY KEY (audit_id),
    INDEX idx_audit_logs_user_created (user_id, created_at),
    INDEX idx_audit_logs_event_created (event, created_at),
    INDEX idx_audit_logs_created (created_at)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '보안 감사 기록 (회원 쪽)';

-- 회원 정지는 상태와 따로 (D-069): status = 수명주기(ACTIVE·DORMANT)만, 정지 = suspended_at(NULL이면 정지 아님).
-- 정지된 회원도 휴면이 정상 진행되고, 휴면에서 복원해도 정지는 남는다. 해제 = NULL. (어드민은 이 열만 쓰고 지운다, US-43)
ALTER TABLE users ADD COLUMN `suspended_at` DATETIME NULL COMMENT '운영자 정지 시각 (NULL = 정지 아님, D-069)' AFTER `status`;
UPDATE users SET suspended_at = NOW(), status = 'ACTIVE' WHERE status = 'SUSPENDED';
-- V2의 status CHECK(이름 없는 첫 CHECK = users_chk_1)를 수명주기 값만으로 바꾼다
ALTER TABLE users DROP CHECK users_chk_1;
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (`status` IN ('ACTIVE', 'DORMANT'));
ALTER TABLE users MODIFY `status` VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태: ACTIVE 사용 중 / DORMANT 휴면 (정지는 suspended_at, D-069)';
