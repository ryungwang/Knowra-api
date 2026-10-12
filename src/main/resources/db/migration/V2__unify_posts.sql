-- ============================================================================
-- V2: 글 모델 통합 — 커뮤니티 글(tbl_comm_post 계열)을 일반 글(tbl_post 계열)로 흡수한다.
--
-- 왜: 두 테이블 가족은 컬럼이 완전히 같고 "커뮤니티 소속" 하나만 달랐다. 그 차이 때문에
--     서비스·피드·저장·알림·행동로그가 전부 두 갈래(POST / COMM)로 갈라져 있었다.
--     tbl_post 에 nullable comm_sn 한 칸을 두면 그 분기가 전부 사라진다.
--
-- 방법: 커뮤니티 글은 새 post_sn 을 받는다(번호가 일반 글과 겹치므로 그대로 옮길 수 없다).
--       옛 번호는 이관 중에만 임시 컬럼(legacy_*)에 들고 있다가 자식 테이블·저장·알림·
--       행동로그·관심도를 새 번호로 바꾼 뒤 지운다. FK 는 원래 없다(논리 참조).
--
-- ⚠️ 배포 후 할 일: Redis 의 피드 캐시(feed:*)와 조회수 키(comm:viewcnt:*)는 옛 번호를
--    들고 있으므로 비워야 한다. 피드는 TTL 로 자연 소멸하지만 조회수 델타는 엉뚱한 글에
--    붙으므로 반드시 지운다.
-- ============================================================================

-- ── 1. tbl_post: 커뮤니티 소속 + 이관 추적용 임시 컬럼 ─────────────────────
ALTER TABLE knowra_post.tbl_post     ADD COLUMN comm_sn BIGINT;
ALTER TABLE knowra_post.tbl_post     ADD COLUMN legacy_comm_post_sn BIGINT;
ALTER TABLE knowra_post.tbl_post_cmt ADD COLUMN legacy_comm_post_cmt_sn BIGINT;
ALTER TABLE knowra_post.tbl_post_cmt ADD COLUMN legacy_prnt_cmt_sn BIGINT;
COMMENT ON COLUMN knowra_post.tbl_post.comm_sn IS '커뮤니티 SN (tbl_comm 논리 참조) — NULL 이면 전체 공개 글';
CREATE INDEX IF NOT EXISTS tbl_post_idx_post_comm ON knowra_post.tbl_post (comm_sn);

-- 수동 INSERT 로 시퀀스가 뒤처져 있을 수 있다. 새 행을 넣기 전에 맞춘다.
SELECT setval(pg_get_serial_sequence('knowra_post.tbl_post',           'post_sn'),
              COALESCE((SELECT MAX(post_sn)      FROM knowra_post.tbl_post),           0) + 1, false);
SELECT setval(pg_get_serial_sequence('knowra_post.tbl_post_cmt',       'post_cmt_sn'),
              COALESCE((SELECT MAX(post_cmt_sn)  FROM knowra_post.tbl_post_cmt),       0) + 1, false);
SELECT setval(pg_get_serial_sequence('knowra_post.tbl_post_like',      'post_like_sn'),
              COALESCE((SELECT MAX(post_like_sn) FROM knowra_post.tbl_post_like),      0) + 1, false);
SELECT setval(pg_get_serial_sequence('knowra_post.tbl_post_tag',       'post_tag_sn'),
              COALESCE((SELECT MAX(post_tag_sn)  FROM knowra_post.tbl_post_tag),       0) + 1, false);
SELECT setval(pg_get_serial_sequence('knowra_post.tbl_post_cmt_react', 'cmt_react_sn'),
              COALESCE((SELECT MAX(cmt_react_sn) FROM knowra_post.tbl_post_cmt_react), 0) + 1, false);

-- ── 2. 커뮤니티 글 → tbl_post (상태와 무관하게 전부 옮긴다 — 삭제된 글도 번호는 살아 있다)
INSERT INTO knowra_post.tbl_post
    (post_typ, user_sn, post_ttl, post_cntnt, view_cnt, like_cnt, cmt_cnt, stat, actvtn_yn,
     creatr_sn, frst_crt_dt, mdfr_sn, mdfcn_dt, comm_sn, legacy_comm_post_sn)
SELECT post_typ, user_sn, post_ttl, post_cntnt, view_cnt, like_cnt, cmt_cnt, stat, actvtn_yn,
       creatr_sn, frst_crt_dt, mdfr_sn, mdfcn_dt, comm_sn, comm_post_sn
FROM knowra_community.tbl_comm_post
ORDER BY comm_post_sn;

-- ── 3. 댓글: 부모 번호는 임시 컬럼에 옛 값을 두고, 새 번호가 전부 생긴 뒤 한 번에 바꾼다
INSERT INTO knowra_post.tbl_post_cmt
    (post_sn, user_sn, prnt_cmt_sn, cmt_cntnt, like_cnt, stat, actvtn_yn,
     creatr_sn, frst_crt_dt, mdfr_sn, mdfcn_dt, legacy_comm_post_cmt_sn, legacy_prnt_cmt_sn)
SELECT p.post_sn, c.user_sn, NULL, c.cmt_cntnt, c.like_cnt, c.stat, c.actvtn_yn,
       c.creatr_sn, c.frst_crt_dt, c.mdfr_sn, c.mdfcn_dt, c.comm_post_cmt_sn, c.prnt_cmt_sn
FROM knowra_community.tbl_comm_post_cmt c
JOIN knowra_post.tbl_post p ON p.legacy_comm_post_sn = c.comm_post_sn
ORDER BY c.comm_post_cmt_sn;

UPDATE knowra_post.tbl_post_cmt n
SET    prnt_cmt_sn = pr.post_cmt_sn
FROM   knowra_post.tbl_post_cmt pr
WHERE  n.legacy_prnt_cmt_sn IS NOT NULL
  AND  pr.legacy_comm_post_cmt_sn = n.legacy_prnt_cmt_sn;

-- ── 4. 좋아요 · 태그 · 댓글 반응 ──────────────────────────────────────────
INSERT INTO knowra_post.tbl_post_like
    (post_sn, user_sn, like_typ, actvtn_yn, creatr_sn, frst_crt_dt, mdfr_sn, mdfcn_dt)
SELECT p.post_sn, l.user_sn, l.like_typ, l.actvtn_yn, l.creatr_sn, l.frst_crt_dt, l.mdfr_sn, l.mdfcn_dt
FROM knowra_community.tbl_comm_post_like l
JOIN knowra_post.tbl_post p ON p.legacy_comm_post_sn = l.comm_post_sn
ORDER BY l.comm_post_like_sn;

INSERT INTO knowra_post.tbl_post_tag
    (post_sn, tag_sn, actvtn_yn, creatr_sn, frst_crt_dt, mdfr_sn, mdfcn_dt)
SELECT p.post_sn, t.tag_sn, t.actvtn_yn, t.creatr_sn, t.frst_crt_dt, t.mdfr_sn, t.mdfcn_dt
FROM knowra_community.tbl_comm_post_tag t
JOIN knowra_post.tbl_post p ON p.legacy_comm_post_sn = t.comm_post_sn
ORDER BY t.comm_post_tag_sn;

INSERT INTO knowra_post.tbl_post_cmt_react
    (post_cmt_sn, user_sn, react_typ, actvtn_yn, creatr_sn, frst_crt_dt, mdfr_sn, mdfcn_dt)
SELECT c.post_cmt_sn, r.user_sn, r.react_typ, r.actvtn_yn, r.creatr_sn, r.frst_crt_dt, r.mdfr_sn, r.mdfcn_dt
FROM knowra_community.tbl_comm_post_cmt_react r
JOIN knowra_post.tbl_post_cmt c ON c.legacy_comm_post_cmt_sn = r.comm_post_cmt_sn
ORDER BY r.cmt_react_sn;

-- ── 5. 저장: 옛 글을 가리키는 COMM 행은 버리고, 나머지는 새 번호로. post_kind 는 없앤다
DELETE FROM knowra_post.tbl_post_save s
WHERE  s.post_kind = 'COMM'
  AND  NOT EXISTS (SELECT 1 FROM knowra_community.tbl_comm_post c WHERE c.comm_post_sn = s.post_sn);

UPDATE knowra_post.tbl_post_save s
SET    post_sn = p.post_sn
FROM   knowra_post.tbl_post p
WHERE  s.post_kind = 'COMM'
  AND  p.legacy_comm_post_sn = s.post_sn;

DROP INDEX IF EXISTS knowra_post.tbl_post_save_uk_post_save_usr;
DROP INDEX IF EXISTS knowra_post.tbl_post_save_idx_post_save_post;
ALTER TABLE knowra_post.tbl_post_save DROP COLUMN post_kind;
CREATE UNIQUE INDEX tbl_post_save_uk_post_save_usr ON knowra_post.tbl_post_save (user_sn, post_sn);
CREATE INDEX        tbl_post_save_idx_post_save_post ON knowra_post.tbl_post_save (post_sn);
COMMENT ON COLUMN knowra_post.tbl_post_save.post_sn IS '게시글 SN (tbl_post 논리 참조)';

-- ── 6. 행동 로그 · 알림 · 관심도: COMM_POST → POST, 번호는 새 번호로 ───────
--    (관심도의 UNIQUE(user, type, target) 은 안전하다 — 새 번호는 기존 POST 번호와 겹치지 않는다)
DELETE FROM knowra_user.tbl_user_action_log a
WHERE  a.target_type = 'COMM_POST'
  AND  NOT EXISTS (SELECT 1 FROM knowra_community.tbl_comm_post c WHERE c.comm_post_sn = a.target_sn);
UPDATE knowra_user.tbl_user_action_log a
SET    target_type = 'POST', target_sn = p.post_sn
FROM   knowra_post.tbl_post p
WHERE  a.target_type = 'COMM_POST' AND p.legacy_comm_post_sn = a.target_sn;

DELETE FROM knowra_user.tbl_user_notif n
WHERE  n.target_kind = 'COMM_POST'
  AND  NOT EXISTS (SELECT 1 FROM knowra_community.tbl_comm_post c WHERE c.comm_post_sn = n.target_sn);
UPDATE knowra_user.tbl_user_notif n
SET    target_kind = 'POST', target_sn = p.post_sn
FROM   knowra_post.tbl_post p
WHERE  n.target_kind = 'COMM_POST' AND p.legacy_comm_post_sn = n.target_sn;

DELETE FROM knowra_user.tbl_user_interest_score i
WHERE  i.target_type = 'COMM_POST'
  AND  NOT EXISTS (SELECT 1 FROM knowra_community.tbl_comm_post c WHERE c.comm_post_sn = i.target_sn);
UPDATE knowra_user.tbl_user_interest_score i
SET    target_type = 'POST', target_sn = p.post_sn
FROM   knowra_post.tbl_post p
WHERE  i.target_type = 'COMM_POST' AND p.legacy_comm_post_sn = i.target_sn;

COMMENT ON COLUMN knowra_user.tbl_user_action_log.target_type     IS 'COMM | POST | USER | TAG';
COMMENT ON COLUMN knowra_user.tbl_user_interest_score.target_type IS 'COMM | POST | USER | TAG';
COMMENT ON COLUMN knowra_user.tbl_user_notif.target_kind          IS '대상종류 POST';

-- ── 7. 옛 테이블 제거 (knowra_community.tbl_post_cmt_react 는 V1 때 엉뚱한 스키마에 생긴 잔재, 0행)
DROP TABLE knowra_community.tbl_comm_post_cmt_react;
DROP TABLE knowra_community.tbl_comm_post_like;
DROP TABLE knowra_community.tbl_comm_post_tag;
DROP TABLE knowra_community.tbl_comm_post_cmt;
DROP TABLE knowra_community.tbl_comm_post;
DROP TABLE knowra_community.tbl_post_cmt_react;

-- ── 8. 임시 컬럼 제거 ──────────────────────────────────────────────────────
ALTER TABLE knowra_post.tbl_post     DROP COLUMN legacy_comm_post_sn;
ALTER TABLE knowra_post.tbl_post_cmt DROP COLUMN legacy_comm_post_cmt_sn;
ALTER TABLE knowra_post.tbl_post_cmt DROP COLUMN legacy_prnt_cmt_sn;
