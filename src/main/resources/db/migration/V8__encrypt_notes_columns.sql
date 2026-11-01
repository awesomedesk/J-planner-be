-- ============================================================
-- V8: 일기·메모 내용 앱 암호화 (US-41, D-061·D-066)
-- 설계 문서: j-planner-product/07-db-design.md 11-8
-- ============================================================
-- 암호문은 새 칸(*_enc)에 둔다: 앱이 AES-256-GCM으로 암호화한 바이트(키 버전 1 + IV 12 + 암호문 + 태그 16).
-- 원래 칸(content·title)은 '암호화 전 원문' 칸이 된다 — 지금 있는 원문은 그대로 두고, 서버를 켤 때 앱(NotesEncryptor)이
-- 암호문 칸으로 옮기고 원문 칸을 비운다 (SQL만으로는 암호화할 수 없어서). 원문인지 암호문인지를 값의 첫 바이트로 추측하지 않는다 (PO #317).
-- 모두 옮겨진 뒤 원문 칸을 지우는 마이그레이션은 배포 뒤 확인하고 따로 한다.
-- 메모 제목 암호문: 255자 × 4바이트 + 29바이트 = 1049 → VARBINARY(1100)
-- (이 파일은 처음 버전이 영구 DB에 적용되기 전에 고쳐 썼다 — 테스트 DB만 적용했었음, 2026-11-01)
ALTER TABLE diaries
    MODIFY `content`     TEXT       NULL COMMENT '암호화 전 원문 (켤 때 content_enc로 옮기고 비움)',
    ADD COLUMN `content_enc` MEDIUMBLOB NULL COMMENT '내용 (AES-256-GCM 암호문)' AFTER `content`,
    ADD CONSTRAINT ck_diaries_content CHECK (`content` IS NOT NULL OR `content_enc` IS NOT NULL);
ALTER TABLE memos
    MODIFY `title`       VARCHAR(255) NULL COMMENT '암호화 전 원문 제목 (켤 때 title_enc로 옮기고 비움)',
    MODIFY `content`     TEXT         NULL COMMENT '암호화 전 원문 내용 (켤 때 content_enc로 옮기고 비움)',
    ADD COLUMN `title_enc`   VARBINARY(1100) NULL COMMENT '제목 (AES-256-GCM 암호문)' AFTER `content`,
    ADD COLUMN `content_enc` MEDIUMBLOB      NULL COMMENT '내용 (AES-256-GCM 암호문)' AFTER `title_enc`;
