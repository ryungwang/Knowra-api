-- ============================================================================
-- V42: 로그인한 기기 기억하기 — 새 기기 로그인 알림
--
-- 기기는 「Chrome · macOS」 같은 이름(세션 목록과 같은 기준)으로 센다. 정확할 필요는 없고 「처음 보는 환경인가」만 본다.
-- IP 는 기준에 넣지 않는다 — 휴대폰은 자리를 옮길 때마다 바뀌어 알림이 쏟아진다.
-- ============================================================================
CREATE TABLE IF NOT EXISTS knowra_user.tbl_known_device (
    USER_SN    BIGINT      NOT NULL,
    DEVICE     VARCHAR(60) NOT NULL,
    FIRST_SEEN TIMESTAMP   NOT NULL,
    LAST_SEEN  TIMESTAMP   NOT NULL,
    PRIMARY KEY (USER_SN, DEVICE)
);
