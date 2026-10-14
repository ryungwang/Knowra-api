-- 비추천(DOWN)은 화면에서 사라졌고 API 도 UP 만 받는다. 그런데 예전 DOWN 행이 남아 있어
-- like_cnt(옛 UP - DOWN 캐시)가 실제 좋아요 행 수와 어긋났다(음수도 생겼다).
-- 남은 DOWN 행을 지우고 like_cnt 를 UP 행 수로 다시 센다.
DELETE FROM knowra_post.tbl_post_like WHERE like_typ <> 'UP';

UPDATE knowra_post.tbl_post p
SET like_cnt = c.cnt
FROM (
    SELECT p2.post_sn, COUNT(l.post_sn) AS cnt
    FROM knowra_post.tbl_post p2
    LEFT JOIN knowra_post.tbl_post_like l ON l.post_sn = p2.post_sn
    GROUP BY p2.post_sn
) c
WHERE p.post_sn = c.post_sn AND p.like_cnt <> c.cnt;
