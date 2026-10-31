-- ============================================================
-- V9: 어드민 회원 현황 집계 뷰 (ADM-04, US-42 작업 12, ADMIN #300)
-- 설계 문서: j-planner-product/07-db-design.md 11-6
-- ============================================================
-- 개인정보 없이 숫자만. 어드민 Grafana 계정(jp_grafana)은 이 뷰만 SELECT한다 (GRANT는 어드민 저장소).
-- 날짜별 숫자는 audit_logs(1년 보관)에서 세므로 1년까지만 남는다.

-- 날짜별(KST) 가입·탈퇴·휴면·복원·휴면 4년 삭제 수. 탈퇴 = reason 없는 ACCOUNT_DELETED, 휴면 삭제 = reason DORMANT_EXPIRED (US-38)
CREATE VIEW v_member_daily AS
SELECT DATE(created_at) AS stat_date,
       SUM(event = 'SIGNUP_COMPLETED') AS signups,
       SUM(event = 'ACCOUNT_DELETED' AND (detail IS NULL OR JSON_EXTRACT(detail, '$.reason') IS NULL)) AS withdrawals,
       SUM(event = 'DORMANT') AS dormant,
       SUM(event = 'DORMANT_RESTORED') AS restored,
       SUM(event = 'ACCOUNT_DELETED' AND JSON_UNQUOTE(JSON_EXTRACT(detail, '$.reason')) = 'DORMANT_EXPIRED') AS dormant_deleted
FROM audit_logs
WHERE event IN ('SIGNUP_COMPLETED', 'ACCOUNT_DELETED', 'DORMANT', 'DORMANT_RESTORED')
GROUP BY DATE(created_at);

-- 지금 회원 수 (한 행). 활성 = ACTIVE이고 정지 아님. 정지는 상태와 따로 세어 휴면과 겹칠 수 있다 (D-069)
CREATE VIEW v_member_status AS
SELECT COALESCE(SUM(status = 'ACTIVE' AND suspended_at IS NULL), 0) AS active,
       COALESCE(SUM(status = 'DORMANT'), 0) AS dormant,
       COALESCE(SUM(suspended_at IS NOT NULL), 0) AS suspended,
       COUNT(*) AS total
FROM users;
