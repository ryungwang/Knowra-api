package com.knowra.community.modmail;

import com.knowra.cmm.event.NotifTriggerEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 운영진에게 문의(모드메일). 보낸 사람 한 명 ↔ 그 커뮤니티 운영진 전체.
 *
 * <ul>
 *   <li>누구나 보낼 수 있다 — 멤버가 아니어도, 강퇴됐어도(이의 제기 창구). 운영진 자신은 보내지 않는다.</li>
 *   <li>운영진은 한 문의함을 같이 본다. 보낸 사람에게 운영진의 답은 이름 대신 「운영진」으로 보인다
 *       (개인에게 화살이 가지 않게. 운영진끼리는 누가 답했는지 본다).</li>
 *   <li>운영진이 닫아도 보낸 사람이 다시 쓰면 열린다 — 닫는 건 「처리함」 표시일 뿐 대화를 막는 게 아니다.</li>
 * </ul>
 * 도배는 RateLimitInterceptor(새 문의·답장), 금칙어·중복은 ContentFilterParamInterceptor 가 막는다.
 */
@Service
@RequiredArgsConstructor
public class ModmailService {

    /** 한 사람이 한 커뮤니티에 동시에 열어 둘 수 있는 문의 수 — 같은 얘기를 여러 통으로 쪼개 운영진 문의함을 덮지 않게 */
    static final int MAX_OPEN_PER_USER = 3;
    static final int SUBJECT_MAX = 120, BODY_MAX = 4000, PAGE = 30;

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    private String roleIn(long commSn, long user) {
        return jdbc.query("SELECT role FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND user_sn = ? AND stat = 'ACTIVE'",
                rs -> rs.next() ? rs.getString(1) : null, commSn, user);
    }

    private boolean isManager(long commSn, long user) {
        String r = roleIn(commSn, user);
        return "OWNER".equals(r) || "ADMIN".equals(r);
    }

    private static String clean(Object v, int max, String emptyMsg) {
        String s = v == null ? "" : String.valueOf(v).trim();
        if (s.isEmpty()) throw new IllegalArgumentException(emptyMsg);
        if (s.length() > max) throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("{0}자까지 쓸 수 있어요.", max));
        return s;
    }

    private Map<String, Object> mail(long mailSn) {
        List<Map<String, Object>> r = jdbc.queryForList("""
                SELECT m.mail_sn, m.comm_sn, m.user_sn, m.subject, m.stat, m.user_unread, m.mod_unread, m.last_at, m.created_at,
                       c.comm_nm, c.comm_dspl_nm
                FROM knowra_community.tbl_comm_modmail m JOIN knowra_community.tbl_comm c ON c.comm_sn = m.comm_sn
                WHERE m.mail_sn = ?""", mailSn);
        if (r.isEmpty()) throw new IllegalArgumentException("문의를 찾을 수 없어요.");
        return r.get(0);
    }

    private static long l(Object o) { return ((Number) o).longValue(); }

    private static String commName(Map<String, Object> m) {
        Object d = m.get("comm_dspl_nm");
        return d != null && !String.valueOf(d).isBlank() ? String.valueOf(d) : String.valueOf(m.get("comm_nm"));
    }

    private void notifyManagers(long commSn, long sender, long mailSn, String commName) {
        for (Long m : jdbc.queryForList("SELECT user_sn FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND stat = 'ACTIVE' AND role IN ('OWNER','ADMIN')", Long.class, commSn))
            events.publishEvent(new NotifTriggerEvent(m, sender, "MODMAIL_NEW", mailSn, "MODMAIL", commName));
    }

    /** 새 문의. params: commSn, subject, body */
    @Transactional
    public Map<String, Object> create(Map<String, Object> params, long me) {
        long commSn = Long.parseLong(String.valueOf(params.get("commSn")));
        Map<String, Object> c = jdbc.queryForList("SELECT comm_nm, comm_dspl_nm, stat FROM knowra_community.tbl_comm WHERE comm_sn = ?", commSn)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("없는 커뮤니티예요."));
        if (!"Y".equals(c.get("stat"))) throw new IllegalArgumentException("없는 커뮤니티예요.");
        if (isManager(commSn, me)) throw new IllegalArgumentException("운영진은 문의함에서 바로 볼 수 있어요.");
        String subject = clean(params.get("subject"), SUBJECT_MAX, "제목을 써 주세요.");
        String body = clean(params.get("body"), BODY_MAX, "내용을 써 주세요.");
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_community.tbl_comm_modmail WHERE comm_sn = ? AND user_sn = ? AND stat = 'OPEN'", Integer.class, commSn, me);
        if (open != null && open >= MAX_OPEN_PER_USER)
            throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("진행 중인 문의가 {0}개 있어요. 이어서 써 주세요.", MAX_OPEN_PER_USER));
        Long mailSn = jdbc.queryForObject("INSERT INTO knowra_community.tbl_comm_modmail (comm_sn, user_sn, subject) VALUES (?,?,?) RETURNING mail_sn", Long.class, commSn, me, subject);
        jdbc.update("INSERT INTO knowra_community.tbl_comm_modmail_msg (mail_sn, sender_sn, from_mod, body) VALUES (?,?,FALSE,?)", mailSn, me, body);
        String name = c.get("comm_dspl_nm") != null && !String.valueOf(c.get("comm_dspl_nm")).isBlank() ? String.valueOf(c.get("comm_dspl_nm")) : String.valueOf(c.get("comm_nm"));
        notifyManagers(commSn, me, mailSn, name);
        return Map.of("mailSn", mailSn);
    }

    /** 문의 한 건과 대화. 보낸 사람이거나 그 커뮤니티 운영진만. 연 쪽의 「안 읽음」을 지운다. */
    @Transactional
    public Map<String, Object> thread(long mailSn, long me) {
        Map<String, Object> m = mail(mailSn);
        long commSn = l(m.get("comm_sn"));
        boolean mine = l(m.get("user_sn")) == me;
        boolean mod = isManager(commSn, me);
        if (!mine && !mod) throw new IllegalArgumentException("문의를 찾을 수 없어요.");   // 있는지조차 알리지 않는다
        if (mod) jdbc.update("UPDATE knowra_community.tbl_comm_modmail SET mod_unread = FALSE WHERE mail_sn = ?", mailSn);
        if (mine) jdbc.update("UPDATE knowra_community.tbl_comm_modmail SET user_unread = FALSE WHERE mail_sn = ?", mailSn);

        List<Map<String, Object>> msgs = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT x.msg_sn, x.sender_sn, x.from_mod, x.body, x.created_at, u.nick_name
                FROM knowra_community.tbl_comm_modmail_msg x LEFT JOIN knowra_user.tbl_user u ON u.user_sn = x.sender_sn
                WHERE x.mail_sn = ? ORDER BY x.msg_sn""", mailSn)) {
            Map<String, Object> o = new LinkedHashMap<>();
            boolean fromMod = Boolean.TRUE.equals(r.get("from_mod"));
            o.put("msgSn", r.get("msg_sn"));
            o.put("fromMod", fromMod);
            o.put("mine", l(r.get("sender_sn")) == me);
            // 보낸 사람에게는 운영진 개인 이름을 주지 않는다 — 화면이 「운영진」으로 쓴다
            if (!fromMod || mod) o.put("nickName", r.get("nick_name"));
            o.put("body", r.get("body"));
            o.put("createdAt", r.get("created_at"));
            msgs.add(o);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mailSn", mailSn);
        out.put("subject", m.get("subject"));
        out.put("stat", m.get("stat"));
        out.put("createdAt", m.get("created_at"));
        out.put("community", Map.of("commSn", commSn, "commNm", m.get("comm_nm"), "commDsplNm", commName(m)));
        out.put("asMod", mod);
        if (mod) out.put("user", jdbc.queryForMap("SELECT user_sn AS \"userSn\", nick_name AS \"nickName\" FROM knowra_user.tbl_user WHERE user_sn = ?", m.get("user_sn")));
        out.put("messages", msgs);
        return out;
    }

    /** 답장. 보낸 사람이 쓰면 운영진에게, 운영진이 쓰면 보낸 사람에게 알린다. 보낸 사람이 쓰면 닫힌 문의가 다시 열린다. */
    @Transactional
    public Map<String, Object> reply(Map<String, Object> params, long me) {
        long mailSn = Long.parseLong(String.valueOf(params.get("mailSn")));
        Map<String, Object> m = mail(mailSn);
        long commSn = l(m.get("comm_sn"));
        long owner = l(m.get("user_sn"));
        boolean mine = owner == me;
        boolean mod = !mine && isManager(commSn, me);
        if (!mine && !mod) throw new IllegalArgumentException("문의를 찾을 수 없어요.");
        String body = clean(params.get("body"), BODY_MAX, "내용을 써 주세요.");
        jdbc.update("INSERT INTO knowra_community.tbl_comm_modmail_msg (mail_sn, sender_sn, from_mod, body) VALUES (?,?,?,?)", mailSn, me, mod, body);
        if (mod) {
            jdbc.update("UPDATE knowra_community.tbl_comm_modmail SET last_at = now(), user_unread = TRUE, mod_unread = FALSE WHERE mail_sn = ?", mailSn);
            // 보낸 이(sender)를 비운다 — 알림에도 운영진 개인 이름이 나가지 않게
            events.publishEvent(new NotifTriggerEvent(owner, null, "MODMAIL_REPLY", mailSn, "MODMAIL", commName(m)));
        } else {
            boolean wasUnread = Boolean.TRUE.equals(m.get("mod_unread"));
            jdbc.update("UPDATE knowra_community.tbl_comm_modmail SET last_at = now(), mod_unread = TRUE, stat = 'OPEN' WHERE mail_sn = ?", mailSn);
            if (!wasUnread) notifyManagers(commSn, me, mailSn, commName(m));   // 이미 안 읽은 게 쌓여 있으면 알림을 또 보내지 않는다
        }
        return Map.of("ok", true);
    }

    /** 처리함(닫기)·다시 열기 — 운영진만. params: mailSn, stat(OPEN|CLOSED) */
    @Transactional
    public Map<String, Object> setStat(Map<String, Object> params, long me) {
        long mailSn = Long.parseLong(String.valueOf(params.get("mailSn")));
        String stat = String.valueOf(params.get("stat"));
        if (!"OPEN".equals(stat) && !"CLOSED".equals(stat)) throw new IllegalArgumentException("잘못된 요청이에요.");
        Map<String, Object> m = mail(mailSn);
        if (!isManager(l(m.get("comm_sn")), me)) throw new IllegalArgumentException("문의를 찾을 수 없어요.");
        jdbc.update("UPDATE knowra_community.tbl_comm_modmail SET stat = ? WHERE mail_sn = ?", stat, mailSn);
        return Map.of("stat", stat);
    }

    /** 운영진 문의함. stat = OPEN | CLOSED, cursor = 이보다 오래된 last_at 의 mailSn 대신 페이지 번호 */
    public Map<String, Object> inbox(long commSn, String stat, int page, long me) {
        if (!isManager(commSn, me)) throw new IllegalArgumentException("운영진만 볼 수 있어요.");
        String st = "CLOSED".equals(stat) ? "CLOSED" : "OPEN";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.mail_sn, m.subject, m.stat, m.mod_unread, m.last_at, u.nick_name,
                       (SELECT x.body FROM knowra_community.tbl_comm_modmail_msg x WHERE x.mail_sn = m.mail_sn ORDER BY x.msg_sn DESC LIMIT 1) AS last_body
                FROM knowra_community.tbl_comm_modmail m JOIN knowra_user.tbl_user u ON u.user_sn = m.user_sn
                WHERE m.comm_sn = ? AND m.stat = ? ORDER BY m.mod_unread DESC, m.last_at DESC LIMIT ? OFFSET ?""",
                commSn, st, PAGE + 1, Math.max(0, page) * PAGE);
        return page(rows, true);
    }

    /** 내가 보낸 문의 — 모든 커뮤니티 */
    public Map<String, Object> mine(int page, long me) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.mail_sn, m.subject, m.stat, m.user_unread, m.last_at, c.comm_nm, c.comm_dspl_nm,
                       (SELECT x.body FROM knowra_community.tbl_comm_modmail_msg x WHERE x.mail_sn = m.mail_sn ORDER BY x.msg_sn DESC LIMIT 1) AS last_body
                FROM knowra_community.tbl_comm_modmail m JOIN knowra_community.tbl_comm c ON c.comm_sn = m.comm_sn
                WHERE m.user_sn = ? ORDER BY m.last_at DESC LIMIT ? OFFSET ?""", me, PAGE + 1, Math.max(0, page) * PAGE);
        return page(rows, false);
    }

    private static Map<String, Object> page(List<Map<String, Object>> rows, boolean asMod) {
        boolean more = rows.size() > PAGE;
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : more ? rows.subList(0, PAGE) : rows) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("mailSn", r.get("mail_sn"));
            o.put("subject", r.get("subject"));
            o.put("stat", r.get("stat"));
            o.put("unread", Boolean.TRUE.equals(r.get(asMod ? "mod_unread" : "user_unread")));
            o.put("lastAt", r.get("last_at"));
            String lb = r.get("last_body") == null ? "" : String.valueOf(r.get("last_body"));
            o.put("preview", lb.length() > 120 ? lb.substring(0, 119) + "…" : lb);
            if (asMod) o.put("nickName", r.get("nick_name"));
            else o.put("community", Map.of("commNm", r.get("comm_nm"), "commDsplNm", r.get("comm_dspl_nm") == null ? r.get("comm_nm") : r.get("comm_dspl_nm")));
            list.add(o);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("hasMore", more);
        return out;
    }

    /** 운영진 배지 숫자 — 안 읽은 진행 중 문의 */
    public long unreadForMods(long commSn) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_community.tbl_comm_modmail WHERE comm_sn = ? AND stat = 'OPEN' AND mod_unread", Long.class, commSn);
        return n == null ? 0 : n;
    }
}
