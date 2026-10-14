-- ============================================================================
-- V28: 커뮤니티 운영진 신고함
--
-- 신고는 사이트 관리자에게만 갔다. 글·댓글 신고에 그 글의 커뮤니티를 붙여, 그 커뮤니티 운영진도 보고 처리하게 한다.
-- 운영진 처리 상태(COMM_STAT)는 사이트 관리자 처리(STAT)와 따로다 — 운영진이 「처리」를 눌러도 사이트 관리자
-- 큐에서는 그대로 보인다(운영진이 자기 편을 덮는 걸 막을 수단이 사이트 관리자뿐이다).
-- ============================================================================
ALTER TABLE knowra_user.tbl_report ADD COLUMN IF NOT EXISTS COMM_SN BIGINT DEFAULT NULL;
ALTER TABLE knowra_user.tbl_report ADD COLUMN IF NOT EXISTS COMM_STAT VARCHAR(10) DEFAULT NULL;
ALTER TABLE knowra_user.tbl_report ADD COLUMN IF NOT EXISTS COMM_HANDLED_BY BIGINT DEFAULT NULL;
ALTER TABLE knowra_user.tbl_report ADD COLUMN IF NOT EXISTS COMM_HANDLED_AT TIMESTAMP DEFAULT NULL;

-- 지난 신고도 커뮤니티를 잇는다
UPDATE knowra_user.tbl_report r SET comm_sn = p.comm_sn, comm_stat = 'OPEN'
FROM knowra_post.tbl_post p WHERE r.target_kind = 'POST' AND p.post_sn = r.target_sn AND p.comm_sn IS NOT NULL AND r.comm_sn IS NULL;
UPDATE knowra_user.tbl_report r SET comm_sn = p.comm_sn, comm_stat = 'OPEN'
FROM knowra_post.tbl_post_cmt c JOIN knowra_post.tbl_post p ON p.post_sn = c.post_sn
WHERE r.target_kind = 'CMT' AND c.post_cmt_sn = r.target_sn AND p.comm_sn IS NOT NULL AND r.comm_sn IS NULL;

CREATE INDEX IF NOT EXISTS idx_report_comm ON knowra_user.tbl_report (COMM_SN, COMM_STAT, REPORT_SN DESC);
