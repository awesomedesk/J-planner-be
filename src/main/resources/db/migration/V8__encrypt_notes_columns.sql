-- ============================================================
-- V8: 일기·메모 내용 앱 암호화 (US-41, D-061·D-066)
-- 설계 문서: j-planner-product/07-db-design.md 11-8
-- ============================================================
-- 앱이 AES-256-GCM으로 암호화한 바이트(키 버전 1 + IV 12 + 암호문 + 태그 16)를 저장하도록 칸 타입만 바꾼다.
-- 지금 있는 원문 바이트는 그대로 남고, 서버를 켤 때 앱(NotesEncryptor)이 읽어 암호화해 다시 저장한다 — SQL만으로는 암호화할 수 없어서.
-- 메모 제목: 255자 × 4바이트 + 29바이트 = 1049 → VARBINARY(1100)
ALTER TABLE diaries MODIFY `content` MEDIUMBLOB NOT NULL COMMENT '내용 (AES-256-GCM 암호문)';
ALTER TABLE memos
    MODIFY `title`   VARBINARY(1100) NULL COMMENT '제목 (AES-256-GCM 암호문)',
    MODIFY `content` MEDIUMBLOB      NULL COMMENT '내용 (AES-256-GCM 암호문)';
