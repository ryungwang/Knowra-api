-- ============================================================================
-- V21: 닉네임·아이디 유일성
--
-- 코드(가입·프로필 수정)가 중복을 막지만, 동시에 두 요청이 들어오면 둘 다 통과한다. 같은 닉네임이 두 줄이 되면
-- 그 닉네임으로 찾는 곳(프로필·DM·신고·멘션·로그인)이 전부 조회 오류로 깨진다. DB 가 마지막으로 막는다.
--
-- ⚠️ 이미 중복이 있으면 인덱스를 만들지 않고 넘어간다(만들려다 실패하면 서버가 뜨지 않는다).
-- 그 경우 아래로 중복을 찾아 정리한 뒤 이 두 줄을 손으로 실행한다:
--   SELECT nick_name, COUNT(*) FROM knowra_user.tbl_user GROUP BY 1 HAVING COUNT(*) > 1;
--   SELECT login_id,  COUNT(*) FROM knowra_user.tbl_user GROUP BY 1 HAVING COUNT(*) > 1;
-- ============================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM knowra_user.tbl_user GROUP BY nick_name HAVING COUNT(*) > 1) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uq_user_nick_name ON knowra_user.tbl_user (nick_name);
    ELSE
        RAISE WARNING 'V21: 중복 닉네임이 있어 uq_user_nick_name 을 만들지 않았다 — 마이그레이션 머리말 참고';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM knowra_user.tbl_user GROUP BY login_id HAVING COUNT(*) > 1) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uq_user_login_id ON knowra_user.tbl_user (login_id);
    ELSE
        RAISE WARNING 'V21: 중복 아이디가 있어 uq_user_login_id 를 만들지 않았다 — 마이그레이션 머리말 참고';
    END IF;
END $$;
