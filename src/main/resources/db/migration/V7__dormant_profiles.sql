-- ============================================================
-- V7: 휴면 (US-38, D-061·D-069)
-- 설계 문서: j-planner-product/07-db-design.md 11-2 (users 휴면 칸, dormant_profiles)
-- ============================================================

-- 휴면 회원의 이메일·이름을 따로 보관한다. 휴면이 되면 users.email·name을 여기로 옮기고 users 칸은 비운다.
-- 다시 로그인해 복원하면 돌려놓고 이 행을 지운다. 휴면 4년 뒤 삭제할 때 같이 지운다.
-- 로그인·비밀번호 찾기는 이 테이블의 이메일로도 찾고, 가입·이메일 변경·구글 가입은 이 이메일도 '이미 가입됨'.
CREATE TABLE dormant_profiles (
    `user_id`   BIGINT          NOT NULL                COMMENT '회원번호',
    `email`     VARCHAR(254)    CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '휴면 전 이메일 (소문자)',
    `name`      VARCHAR(50)     NULL                    COMMENT '휴면 전 이름',
    `moved_at`  DATETIME        NOT NULL                COMMENT '옮긴 시각 (= 휴면 된 시각)',
    PRIMARY KEY (user_id),
    CONSTRAINT uk_dormant_profiles_email UNIQUE (email),
    CONSTRAINT ck_dormant_profiles_email_lower CHECK (`email` = LOWER(`email`)),
    CONSTRAINT fk_dormant_profiles_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '휴면 회원의 이메일·이름 (분리 보관)';
