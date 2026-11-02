-- ============================================================
-- V11: 일기·메모 원문 칸과 암호문 칸 나누기 (US-41, PO #317 3차)
-- 설계 문서: j-planner-product/07-db-design.md 11-8
-- ============================================================
-- 처음 설계(V8·V10)는 원문과 암호문을 한 칸(content·title)에 두고 첫 바이트로 구분했다. 이제 암호문은 새 칸(*_enc),
-- 암호화 전 원문은 원래 칸(content·title, 다시 TEXT·VARCHAR)에 둔다. 앱은 값의 모양으로 추측하지 않는다.
-- V8·V10은 공유 로컬 DB에 이미 적용돼 있어 고치지 않고(체크섬 유지) 이 파일로 앞으로 옮긴다.
--
-- 옮기는 규칙 (V8~V10이 같은 마이그레이션에서 함께 적용됐고, 그 뒤 앱은 암호문만 썼다는 사실에 근거):
--   첫 바이트 0x00 = V10이 표시한 암호화 전 원문 → 표시를 떼고 원문 칸에 남김
--   그 밖(암호문 = 키 버전 1, 그리고 손상값) → 암호문 칸으로 그대로 옮김. 손상값은 복호화할 때 오류로 드러나고 덮어쓰지 않는다
-- 켤 때 앱(NotesEncryptor)이 원문 칸의 값을 암호화해 암호문 칸으로 옮긴다.

-- 1. 일기
ALTER TABLE diaries
    MODIFY `content` MEDIUMBLOB NULL,
    ADD COLUMN `content_enc` MEDIUMBLOB NULL COMMENT '내용 (AES-256-GCM 암호문)' AFTER `content`;
UPDATE diaries SET content_enc = content, content = NULL WHERE NOT (LENGTH(content) >= 1 AND ASCII(content) = 0);
UPDATE diaries SET content = SUBSTRING(content, 2) WHERE content IS NOT NULL;
ALTER TABLE diaries
    MODIFY `content` TEXT NULL COMMENT '암호화 전 원문 (켤 때 content_enc로 옮기고 비움)',
    ADD CONSTRAINT ck_diaries_content CHECK (`content` IS NOT NULL OR `content_enc` IS NOT NULL);

-- 2. 메모 (제목·내용 모두 비어 있을 수 있음)
ALTER TABLE memos
    ADD COLUMN `title_enc`   VARBINARY(1100) NULL COMMENT '제목 (AES-256-GCM 암호문)' AFTER `content`,
    ADD COLUMN `content_enc` MEDIUMBLOB      NULL COMMENT '내용 (AES-256-GCM 암호문)' AFTER `title_enc`;
UPDATE memos SET title_enc = title, title = NULL WHERE title IS NOT NULL AND NOT (LENGTH(title) >= 1 AND ASCII(title) = 0);
UPDATE memos SET title = SUBSTRING(title, 2) WHERE title IS NOT NULL;
UPDATE memos SET content_enc = content, content = NULL WHERE content IS NOT NULL AND NOT (LENGTH(content) >= 1 AND ASCII(content) = 0);
UPDATE memos SET content = SUBSTRING(content, 2) WHERE content IS NOT NULL;
ALTER TABLE memos
    MODIFY `title`   VARCHAR(255) NULL COMMENT '암호화 전 원문 제목 (켤 때 title_enc로 옮기고 비움)',
    MODIFY `content` TEXT         NULL COMMENT '암호화 전 원문 내용 (켤 때 content_enc로 옮기고 비움)';
