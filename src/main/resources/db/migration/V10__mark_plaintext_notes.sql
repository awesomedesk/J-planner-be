-- ============================================================
-- V10: 암호화 전 일기·메모 원문에 '원문' 표시를 붙인다 (US-41, PO #317 1번)
-- 설계 문서: j-planner-product/07-db-design.md 11-8
-- ============================================================
-- 앱은 표시(첫 바이트 0x00)가 있는 값만 '암호화 전 원문'으로 읽고, 켤 때(NotesEncryptor) 암호화해 다시 저장한다.
-- 표시 없는 값은 암호문(키 버전 1)이어야 하며, 그 밖의 값은 손상으로 보고 읽지 않는다 — 손상값을 원문처럼 다시 암호화하지 않게.
-- V8 뒤 앱이 쓴 값은 이미 암호문(첫 바이트 1, 29바이트 이상)이라 건드리지 않는다.
UPDATE diaries SET content = CONCAT(X'00', content) WHERE NOT (LENGTH(content) >= 29 AND ASCII(content) = 1);
UPDATE memos SET title = CONCAT(X'00', title) WHERE title IS NOT NULL AND NOT (LENGTH(title) >= 29 AND ASCII(title) = 1);
UPDATE memos SET content = CONCAT(X'00', content) WHERE content IS NOT NULL AND NOT (LENGTH(content) >= 29 AND ASCII(content) = 1);
