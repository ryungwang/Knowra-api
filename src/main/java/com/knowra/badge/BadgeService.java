package com.knowra.badge;

import com.knowra.cmm.event.NotifTriggerEvent;
import com.knowra.community.entity.QTblComm;
import com.knowra.community.entity.QTblCommMbr;
import com.knowra.poll.entity.QTblPoll;
import com.knowra.post.entity.QTblPost;
import com.knowra.post.entity.QTblPostCmt;
import com.knowra.user.entity.QTblUserFlwr;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 뱃지 판정. 통계 7개를 세고, 목표를 넘은 뱃지 중 아직 없는 것을 준다(알림 BADGE 한 번).
 * 부르는 때: 글·댓글을 쓴 뒤(그 사람만), 프로필 뱃지를 볼 때. 통계 쿼리는 카운트 7번이라 가볍다.
 */
@Service
@RequiredArgsConstructor
public class BadgeService {

    @PersistenceContext
    private EntityManager em;
    private final TblUserBadgeRepository repo;
    private final ApplicationEventPublisher events;
    private final com.knowra.user.repository.TblUserStngRepository userStngRepository;

    public Map<String, Long> stats(long userSn) {
        JPAQueryFactory q = new JPAQueryFactory(em);
        QTblPost p = QTblPost.tblPost;
        QTblPostCmt c = QTblPostCmt.tblPostCmt;
        QTblUserFlwr f = QTblUserFlwr.tblUserFlwr;
        QTblComm cm = QTblComm.tblComm;
        QTblCommMbr m = QTblCommMbr.tblCommMbr;
        QTblPoll pl = QTblPoll.tblPoll;
        Map<String, Long> s = new HashMap<>();
        s.put("posts", nz(q.select(p.count()).from(p).where(p.userSn.eq(userSn), p.actvtnYn.eq("Y")).fetchOne()));
        s.put("comments", nz(q.select(c.count()).from(c).where(c.userSn.eq(userSn), c.stat.eq("ACTIVE")).fetchOne()));
        s.put("likesGot", nz(q.select(p.likeCnt.sum().longValue()).from(p).where(p.userSn.eq(userSn), p.actvtnYn.eq("Y")).fetchOne()));
        s.put("followers", nz(q.select(f.count()).from(f).where(f.flwngUserSn.eq(userSn), f.actvtnYn.eq("Y")).fetchOne()));
        s.put("founded", nz(q.select(m.count()).from(m).join(cm).on(cm.commSn.eq(m.commSn))
                .where(m.userSn.eq(userSn), m.role.eq("OWNER"), cm.actvtnYn.eq("Y")).fetchOne()));
        s.put("joined", nz(q.select(m.count()).from(m).where(m.userSn.eq(userSn), m.stat.eq("ACTIVE")).fetchOne()));
        s.put("polls", nz(q.select(pl.count()).from(pl).join(p).on(p.postSn.eq(pl.postSn)).where(p.userSn.eq(userSn), p.actvtnYn.eq("Y")).fetchOne()));
        return s;
    }

    private static long nz(Number n) { return n == null ? 0 : n.longValue(); }

    /** 새로 얻은 뱃지 키 목록. 같은 순간 두 번 불려도 UNIQUE 가 막고, 그 경우는 조용히 넘어간다. */
    public List<String> evaluate(long userSn) {
        Map<String, Long> st = stats(userSn);
        Set<String> have = new HashSet<>();
        repo.findAllByUserSn(userSn).forEach(b -> have.add(b.getBadgeKey()));
        List<String> got = new ArrayList<>();
        for (BadgeCatalog.Badge b : BadgeCatalog.ALL) {
            if (have.contains(b.key()) || st.getOrDefault(b.stat(), 0L) < b.goal()) continue;
            try {
                repo.saveAndFlush(TblUserBadge.builder().userSn(userSn).badgeKey(b.key()).build());
                got.add(b.key());
            } catch (DataIntegrityViolationException dup) { /* 다른 요청이 먼저 줬다 */ }
        }
        // 알림은 한 번만 — 오래 쓴 사람이 뱃지 도입 후 처음 판정되면 한꺼번에 여러 개를 얻는다(알림 폭탄 방지)
        if (!got.isEmpty()) {
            // 알림 문장에 그대로 끼워 들어간다 — 뱃지 이름도 받는 사람(=본인)의 언어로. 남의 프로필을 볼 때도 판정되니 요청 언어가 아니다
            var stng = userStngRepository.findByUserSn(userSn);
            String lang = stng == null || stng.getLang() == null ? com.knowra.cmm.i18n.Lang.KO : stng.getLang();
            String first = com.knowra.cmm.i18n.Msg.tl(lang, BadgeCatalog.byKey(got.get(0)).name());
            String label = got.size() == 1 ? com.knowra.cmm.i18n.Msg.tl(lang, "「{0}」", first) : com.knowra.cmm.i18n.Msg.tl(lang, "「{0}」 외 {1}개", first, got.size() - 1);
            events.publishEvent(new NotifTriggerEvent(userSn, null, "BADGE", null, "BADGE", label));
        }
        return got;
    }

    /** 프로필용 — 전체 목록에 얻은 시각, 본인이면 진행도까지. */
    public List<Map<String, Object>> view(long userSn, boolean mine) {
        evaluate(userSn);
        Map<String, TblUserBadge> have = new HashMap<>();
        repo.findAllByUserSn(userSn).forEach(b -> have.put(b.getBadgeKey(), b));
        Map<String, Long> st = mine ? stats(userSn) : Map.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (BadgeCatalog.Badge b : BadgeCatalog.ALL) {
            TblUserBadge e = have.get(b.key());
            if (e == null && !mine) continue;   // 남의 프로필엔 얻은 것만
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", b.key()); m.put("name", com.knowra.cmm.i18n.Msg.t(b.name())); m.put("desc", com.knowra.cmm.i18n.Msg.t(b.desc())); m.put("tone", b.tone());
            m.put("earnedAt", e == null ? null : e.getEarnedAt());
            if (mine) { m.put("progress", Math.min(st.getOrDefault(b.stat(), 0L), b.goal())); m.put("goal", b.goal()); }
            out.add(m);
        }
        return out;
    }
}
