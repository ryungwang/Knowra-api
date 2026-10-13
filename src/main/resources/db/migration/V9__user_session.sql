-- ============================================================================
-- V9: 로그인한 기기(세션) — 설정 > 보안의 「로그인한 기기」, 「이 기기 로그아웃」.
--
-- 토큰은 여전히 JWT 지만, 로그인할 때마다 세션 한 줄(SID)을 만들고 토큰에 sid 를 싣는다.
-- 리프레시는 그 줄이 살아 있을 때만 된다 → 줄을 지우면 그 기기는 다음 갱신에서 끊긴다.
-- 바로 끊어야 하므로 지운 sid 는 Redis 에도 액세스 토큰 수명만큼 올려 두고 필터가 거절한다(SessionService).
-- 30일 넘게 안 쓴 세션은 리프레시 수명(7일)을 이미 넘겼으니 목록에서 빼고 정리한다.
-- ============================================================================
CREATE TABLE IF NOT EXISTS knowra_user.tbl_user_session
(
    SID          VARCHAR(36) NOT NULL,
    USER_SN      BIGINT NOT NULL,
    USER_AGENT   VARCHAR(300) DEFAULT NULL,
    IP           VARCHAR(64) DEFAULT NULL,
    CREATED_AT   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    LAST_SEEN_AT TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (SID),
    CONSTRAINT FK_SESSION_USER FOREIGN KEY (USER_SN) REFERENCES knowra_user.tbl_user (USER_SN) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_session_user ON knowra_user.tbl_user_session (USER_SN, LAST_SEEN_AT DESC);
