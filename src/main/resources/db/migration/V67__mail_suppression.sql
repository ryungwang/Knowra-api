-- ============================================================================
-- V67: 메일 반송·스팸 신고 주소 — 더 보내지 않는다
--
-- 없는 주소(영구 반송)나 스팸으로 신고한 주소로 계속 보내면 발송 평판이 떨어져 다른 사람 메일까지
-- 스팸함으로 간다. Amazon SES 가 SNS 로 알려 주면(POST /api/mail/sns) 여기 적는다.
--   BOUNCE    — 영구 반송. 본인이 요청한 메일(인증 코드·비밀번호 재설정) 말고는 보내지 않는다.
--   COMPLAINT — 스팸 신고. 주간 소식 같은 대량 메일을 보내지 않고 주간 소식 설정도 끈다.
-- 그 주소로 인증 코드를 확인하거나(받을 수 있다는 증거) 본인이 주간 소식을 다시 켜면 지운다.
-- ============================================================================
CREATE TABLE knowra_com.tbl_mail_suppression (
    email       VARCHAR(254) PRIMARY KEY,          -- 소문자
    reason      VARCHAR(10)  NOT NULL CHECK (reason IN ('BOUNCE', 'COMPLAINT')),
    detail      VARCHAR(300),                      -- 반송 사유(진단 코드) — 운영자가 볼 때
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);
