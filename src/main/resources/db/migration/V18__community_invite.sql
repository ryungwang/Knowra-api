-- ============================================================================
-- V18: 커뮤니티 초대 링크
--
-- 링크 하나 = 한 사람이 한 커뮤니티에 만든 초대. 7일 뒤 만료, 만든 사람이 취소할 수 있다.
-- 승인제 커뮤니티는 운영진(OWNER/ADMIN)만 만들고, 운영진 링크로 들어오면 승인 없이 바로 멤버다.
-- 운영진 여부는 "만든 때"가 아니라 "쓰는 때" 기준으로 다시 본다 — 운영진에서 내려온 사람의 옛 링크가
-- 계속 승인을 건너뛰게 두지 않으려고.
-- ============================================================================
CREATE TABLE IF NOT EXISTS knowra_community.tbl_comm_invite
(
    INVITE_CODE VARCHAR(16) NOT NULL,
    COMM_SN     BIGINT NOT NULL,
    CREATED_BY  BIGINT NOT NULL,
    EXPIRES_AT  TIMESTAMP(6) NOT NULL,
    USE_CNT     INT NOT NULL DEFAULT 0,
    REVOKED     BOOLEAN NOT NULL DEFAULT FALSE,
    FRST_CRT_DT TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (INVITE_CODE)
);
CREATE INDEX IF NOT EXISTS idx_comm_invite_owner ON knowra_community.tbl_comm_invite (COMM_SN, CREATED_BY);
