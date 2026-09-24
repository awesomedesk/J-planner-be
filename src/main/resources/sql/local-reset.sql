-- ============================================================
-- 로컬 DB 초기화: jp DB를 지우고 새로 만든 뒤 테이블 + 테스트 데이터를 넣는다
-- ⚠️ 로컬 jp DB의 모든 데이터가 지워진다. 운영 DB에는 쓰지 않는다.
--
-- 실행 (이 파일이 있는 폴더에서, SOURCE가 상대 경로라서):
--   cd J-planner-BE/src/main/resources/sql
--   mysql -u jplanner -p < local-reset.sql
-- ============================================================

DROP DATABASE IF EXISTS jp;
CREATE DATABASE jp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE jp;

SOURCE schema.sql;
SOURCE sample-data.sql;

-- 확인
SELECT 'categories' AS 테이블, COUNT(*) AS 개수 FROM categories
UNION ALL SELECT 'calendars', COUNT(*) FROM calendars
UNION ALL SELECT 'calendar_details', COUNT(*) FROM calendar_details
UNION ALL SELECT 'todos', COUNT(*) FROM todos
UNION ALL SELECT 'ddays', COUNT(*) FROM ddays
UNION ALL SELECT 'diaries', COUNT(*) FROM diaries
UNION ALL SELECT 'memos', COUNT(*) FROM memos;
