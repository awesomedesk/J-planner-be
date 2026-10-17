-- ============================================================
-- V6: 구글 로그인·연결 (US-36, D-061)
-- 설계 문서: j-planner-product/07-db-design.md 11-2 (user_identities)
-- ============================================================

-- 회원에 연결된 바깥 로그인 수단. 지금은 구글(OpenID Connect)만.
-- 구글 계정은 sub(구글이 정한 바뀌지 않는 번호)로만 찾는다 — 이메일은 바뀔 수 있다.
-- (provider, provider_subject) 유일: 다른 회원에 연결된 구글은 DB가 막는다. (user_id, provider) 유일: 회원당 구글 하나. 합치기 없음.
CREATE TABLE user_identities (
    `identity_id`       BIGINT          NOT NULL AUTO_INCREMENT COMMENT '연결 번호',
    `user_id`           BIGINT          NOT NULL                COMMENT '회원번호',
    `provider`          VARCHAR(10)     NOT NULL                COMMENT 'GOOGLE',
    `provider_subject`  VARCHAR(255)    COLLATE utf8mb4_bin NOT NULL COMMENT '구글 sub (대소문자 구분)',
    `email_at_link`     VARCHAR(254)    COLLATE utf8mb4_bin NULL COMMENT '연결할 때의 구글 이메일 (소문자, 내 계정 화면 표시용)',
    `linked_at`         DATETIME        NOT NULL                COMMENT '연결한 시각',
    PRIMARY KEY (identity_id),
    CONSTRAINT uk_user_identities_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_user_identities_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_user_identities_provider CHECK (`provider` IN ('GOOGLE')),
    CONSTRAINT fk_user_identities_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '바깥 로그인 수단 연결 (구글)';
