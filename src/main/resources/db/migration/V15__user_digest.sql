-- 주간 소식 메일 수신 동의. 기본은 받지 않음 — 광고성으로 읽힐 수 있는 정기 메일은 동의한 사람에게만 보낸다.
ALTER TABLE knowra_user.tbl_user_stng ADD COLUMN digest_yn VARCHAR(1) NOT NULL DEFAULT 'N';
