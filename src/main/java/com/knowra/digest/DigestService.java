package com.knowra.digest;

import com.knowra.cmm.i18n.Fmt;
import com.knowra.cmm.i18n.Msg;
import com.knowra.cmm.messaging.MailService;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.community.entity.QTblComm;
import com.knowra.community.entity.QTblCommMbr;
import com.knowra.event.QTblCommEvent;
import com.knowra.post.entity.QTblPost;
import com.knowra.safety.entity.QTblPostHide;
import com.knowra.safety.entity.QTblUserBlock;
import com.knowra.user.entity.QTblUser;
import com.knowra.user.entity.QTblUserStng;
import com.knowra.user.entity.TblUser;
import com.knowra.user.repository.TblUserStngRepository;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.*;

/**
 * 주간 소식 메일 — 「이번 주 내 동네에서」. 내가 들어간 커뮤니티의 지난 7일 인기 글, 다가오는 7일의 모임,
 * 커뮤니티별 새 글 수를 한 통에 담는다.
 *
 * <ul>
 *   <li><b>동의한 사람에게만</b>({@code tbl_user_stng.digest_yn = 'Y'}, 기본 N). 매 메일에 한 번에 끄는 링크를 단다.</li>
 *   <li>담을 게 없으면 보내지 않는다 — 빈 메일은 구독 해지의 지름길이다.</li>
 *   <li>숨긴 글·내가 차단한 사람의 글·내 글은 뺀다(피드와 같은 규칙).</li>
 *   <li>같은 주에 두 번 가지 않게 Redis 에 「주차:사용자」 표식을 남긴다(재시작·다중 인스턴스 대비).</li>
 * </ul>
 */
@Slf4j
@Service
public class DigestService {

    private static final int REDIS_DB = 15;

    @PersistenceContext
    private EntityManager em;
    private final MailService mail;
    private final RedisApiService redis;
    private final TblUserStngRepository stngs;
    private final String front;
    private final byte[] secret;

    public DigestService(MailService mail, RedisApiService redis, TblUserStngRepository stngs,
                         @Value("${knowra.front.base-url:http://localhost:5111}") String front,
                         @Value("${jwt.secret}") String secret) {
        this.mail = mail; this.redis = redis; this.stngs = stngs;
        this.front = front.replaceAll("/+$", "");
        this.secret = ("digest-off:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    private JPAQueryFactory q() { return new JPAQueryFactory(em); }

    public record Post(long postSn, String title, String commNm, String commName, int likeCnt, int cmtCnt) {}
    public record Event(String title, String commNm, String commName, LocalDateTime startsAt) {}
    public record Comm(String commNm, String commName, long newPosts) {}
    public record Digest(String name, List<Post> posts, List<Event> events, List<Comm> comms) {
        public boolean isEmpty() { return posts.isEmpty() && events.isEmpty(); }
    }

    /** 한 사람 몫을 모은다. 보낼지 말지와 무관하게 미리보기도 이걸 쓴다. */
    @Transactional(readOnly = true)
    public Digest build(long userSn, LocalDateTime now) {
        QTblUser u = QTblUser.tblUser;
        QTblComm c = QTblComm.tblComm;
        QTblCommMbr m = QTblCommMbr.tblCommMbr;
        QTblPost p = QTblPost.tblPost;
        QTblCommEvent e = QTblCommEvent.tblCommEvent;
        QTblPostHide h = QTblPostHide.tblPostHide;
        QTblUserBlock b = QTblUserBlock.tblUserBlock;

        TblUser me = q().selectFrom(u).where(u.userSn.eq(userSn)).fetchOne();
        if (me == null) return new Digest("", List.of(), List.of(), List.of());
        String name = me.getNickName() != null && !me.getNickName().isBlank() ? me.getNickName() : me.getName();

        List<Long> commSns = q().select(m.commSn).from(m).join(c).on(c.commSn.eq(m.commSn))
                .where(m.userSn.eq(userSn), m.stat.eq("ACTIVE"), m.actvtnYn.eq("Y"), c.stat.eq("Y"), c.actvtnYn.eq("Y"))
                .fetch();
        if (commSns.isEmpty()) return new Digest(name, List.of(), List.of(), List.of());

        LocalDateTime weekAgo = now.minusDays(7);
        var visible = p.commSn.in(commSns)
                .and(p.frstCrtDt.after(weekAgo))
                .and(p.stat.eq("ACTIVE")).and(p.actvtnYn.eq("Y"))
                .and(p.userSn.ne(userSn))
                .and(p.postSn.notIn(JPAExpressions.select(h.postSn).from(h).where(h.userSn.eq(userSn))))
                .and(p.userSn.notIn(JPAExpressions.select(b.blockedSn).from(b).where(b.userSn.eq(userSn))));

        List<Post> posts = q().select(p.postSn, p.postTtl, c.commNm, c.commDsplNm, p.likeCnt, p.cmtCnt)
                .from(p).join(c).on(c.commSn.eq(p.commSn))
                .where(visible)   // 조용한 동네도 새 글은 보여 준다 — 반응 많은 순, 같으면 최신
                .orderBy(p.likeCnt.multiply(2).add(p.cmtCnt).desc(), p.postSn.desc())
                .limit(5).fetch().stream()
                .map(t -> new Post(t.get(p.postSn), t.get(p.postTtl), t.get(c.commNm), label(t, c), t.get(p.likeCnt), t.get(p.cmtCnt)))
                .toList();

        List<Event> events = q().select(e.title, c.commNm, c.commDsplNm, e.startsAt)
                .from(e).join(c).on(c.commSn.eq(e.commSn))
                .where(e.commSn.in(commSns), e.stat.eq("ACTIVE"), e.startsAt.between(now, now.plusDays(7)))
                .orderBy(e.startsAt.asc()).limit(3).fetch().stream()
                .map(t -> new Event(t.get(e.title), t.get(c.commNm), label(t, c), t.get(e.startsAt)))
                .toList();

        List<Comm> comms = q().select(c.commNm, c.commDsplNm, p.count())
                .from(p).join(c).on(c.commSn.eq(p.commSn))
                .where(visible)
                .groupBy(c.commNm, c.commDsplNm)
                .orderBy(p.count().desc()).limit(5).fetch().stream()
                .map(t -> new Comm(t.get(c.commNm), label(t, c), Optional.ofNullable(t.get(p.count())).orElse(0L)))
                .toList();

        return new Digest(name, posts, events, comms);
    }

    private static String label(Tuple t, QTblComm c) {
        String d = t.get(c.commDsplNm);
        return d != null && !d.isBlank() ? d : t.get(c.commNm);
    }

    /** 받는 사람의 언어·시간대 — 설정 줄이 없으면 한국어·서울 */
    public record Prefs(String lang, String tz) {}

    public Prefs prefs(long userSn) {
        var s = stngs.findByUserSn(userSn);
        return s == null ? new Prefs("ko", null) : new Prefs(s.getLang() == null ? "ko" : s.getLang(), s.getTimeZone());
    }

    public String subject(Digest d, String lang) {
        if (!d.posts().isEmpty()) return Msg.tl(lang, "[knowra] 이번 주 내 동네에서 — {0}", cut(d.posts().get(0).title(), 30));
        return Msg.tl(lang, "[knowra] 이번 주 내 동네에서 — 다가오는 모임 {0}개", d.events().size());
    }

    public String render(Digest d, long userSn, Prefs p) {
        String lang = p.lang();
        StringBuilder sb = new StringBuilder();
        sb.append(Msg.tl(lang, "{0}님, 이번 주 내 동네 소식이에요.", d.name())).append("\n\n");
        if (!d.posts().isEmpty()) {
            sb.append(Msg.tl(lang, "■ 이번 주 눈에 띈 글")).append("\n");
            for (Post x : d.posts()) {
                sb.append("· [").append(x.commName()).append("] ").append(cut(x.title(), 60))
                  .append(x.likeCnt() + x.cmtCnt() > 0 ? "  " + Msg.tl(lang, "(좋아요 {0} · 댓글 {1})", x.likeCnt(), x.cmtCnt()) : "").append("\n")
                  .append("  ").append(front).append("/community/").append(x.commNm()).append("/posts/").append(x.postSn()).append("\n");
            }
            sb.append("\n");
        }
        if (!d.events().isEmpty()) {
            sb.append(Msg.tl(lang, "■ 다가오는 모임")).append("\n");
            for (Event x : d.events()) {
                // 받는 사람의 언어·시간대로, 요일을 덧붙인다 — 「2026. 10. 18. 오후 7:00 (일)」 / 「Oct 18, 2026, 7:00 PM (Sun)」
                String dow = x.startsAt().atZone(java.time.ZoneId.systemDefault()).withZoneSameInstant(Fmt.zone(p.tz()))
                        .getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Fmt.locale(lang));
                sb.append("· ").append(Fmt.dateTime(x.startsAt(), lang, p.tz())).append(" (").append(dow).append(")")
                  .append("  [").append(x.commName()).append("] ").append(cut(x.title(), 60)).append("\n")
                  .append("  ").append(front).append("/community/").append(x.commNm()).append("\n");
            }
            sb.append("\n");
        }
        if (!d.comms().isEmpty()) {
            sb.append(Msg.tl(lang, "■ 이번 주 새 글")).append("\n");
            for (Comm x : d.comms()) sb.append("· ").append(Msg.tl(lang, "{0} {1}개", x.commName(), x.newPosts())).append("\n");
            sb.append("\n");
        }
        sb.append("—\n")
          .append(Msg.tl(lang, "설정에서 「주간 소식 메일」을 켠 분께 매주 월요일 아침에 보내요.")).append("\n")
          .append(Msg.tl(lang, "그만 받기: {0}", unsubscribeUrl(userSn))).append("\n");
        return sb.toString();
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        return s.codePointCount(0, s.length()) <= n ? s : s.substring(0, s.offsetByCodePoints(0, n)) + "…";
    }

    // ── 한 번에 끄기 링크 ─ 로그인 없이 눌러도 되게 사용자 번호에 서명을 붙인다 ─────────────────
    public String token(long userSn) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] sig = mac.doFinal(String.valueOf(userSn).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(sig, 16));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    public String unsubscribeUrl(long userSn) { return front + "/digest/off?u=" + userSn + "&t=" + token(userSn); }

    /** 서명이 맞으면 끈다. 이미 꺼져 있어도 성공(링크를 두 번 눌러도 같은 화면). */
    @Transactional
    public void unsubscribe(long userSn, String t) {
        if (t == null || !MessageDigest.isEqual(token(userSn).getBytes(StandardCharsets.UTF_8), t.getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("링크가 올바르지 않아요. 설정 화면에서 꺼 주세요.");
        var s = stngs.findByUserSn(userSn);
        if (s != null && !"N".equals(s.getDigestYn())) { s.setDigestYn("N"); stngs.save(s); }
    }

    // ── 발송 ───────────────────────────────────────────────────────────────────────────────────
    /**
     * 매시 정각 — <b>그 사람의 시간대로 월요일 오전 9시</b>인 사람에게만 보낸다. 예전엔 서울 월요일 9시에 전원에게
     * 보내서, 뉴욕 사용자는 일요일 밤 8시에 받았다. 같은 주에 두 번 가지 않게 주 표식(Redis)은 그대로 쓴다.
     */
    @Scheduled(cron = "${knowra.digest.cron:0 0 * * * *}", zone = "UTC")
    public void weekly() {
        Map<String, Integer> r = sendAll(LocalDateTime.now(), java.time.Instant.now());
        if (r.get("target") > 0) log.info("[DIGEST] 주간 소식 발송 {}", r);
    }

    /** 지금 이 사람의 시계가 월요일 9시인가 */
    static boolean dueAt(java.time.Instant now, String tz) {
        var local = now.atZone(com.knowra.cmm.i18n.Fmt.zone(tz));
        return local.getDayOfWeek() == java.time.DayOfWeek.MONDAY && local.getHour() == 9;
    }

    /** 동의한 사람 전원에게 보낸다(관리자 수동 발송). 사람마다 실패를 따로 삼켜 한 명 때문에 나머지가 멈추지 않게 한다. */
    public Map<String, Integer> sendAll(LocalDateTime now) { return sendAll(now, null); }

    /** dueOnly 가 있으면 그 시각에 월요일 9시인 사람만 */
    public Map<String, Integer> sendAll(LocalDateTime now, java.time.Instant dueOnly) {
        QTblUser u = QTblUser.tblUser;
        QTblUserStng s = QTblUserStng.tblUserStng;
        List<Tuple> rows = q().select(u.userSn, u.email).from(u).join(s).on(s.userSn.eq(u.userSn))
                .where(s.digestYn.eq("Y"), u.status.eq("Y"), u.actvtnYn.eq("Y"), u.email.isNotNull(),
                        u.email.like("%_@_%.__%"))   // 주소 꼴이 아닌 값(옛 시드의 'deer' 등)은 보내 봐야 실패만 쌓인다
                .fetch();
        if (dueOnly != null) rows = rows.stream().filter(t -> dueAt(dueOnly, prefs(t.get(u.userSn)).tz())).toList();
        String week = now.get(IsoFields.WEEK_BASED_YEAR) + "-W" + now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int sent = 0, empty = 0, dup = 0, failed = 0;
        for (Tuple t : rows) {
            long sn = t.get(u.userSn);
            try {
                Digest d = build(sn, now);
                if (d.isEmpty()) { empty++; continue; }
                if (!redis.setIfAbsent(REDIS_DB, "digest:sent:" + week + ":" + sn, "1", 8L * 24 * 3600)) { dup++; continue; }
                Prefs p = prefs(sn);
                try { mail.send(t.get(u.email), subject(d, p.lang()), render(d, sn, p)); sent++; }
                catch (RuntimeException ex) { redis.delRedis(REDIS_DB, "digest:sent:" + week + ":" + sn); throw ex; }   // 실패하면 표식을 지워 다음 실행에서 다시
            } catch (RuntimeException ex) {
                failed++;
                log.warn("[DIGEST] userSn={} 발송 실패: {}", sn, ex.getMessage());
            }
        }
        return Map.of("target", rows.size(), "sent", sent, "empty", empty, "duplicate", dup, "failed", failed);
    }
}
