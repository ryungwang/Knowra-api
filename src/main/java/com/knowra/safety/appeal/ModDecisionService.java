package com.knowra.safety.appeal;

import com.knowra.cmm.event.NotifTriggerEvent;
import com.knowra.cmm.service.RedisApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 사이트 운영 조치 기록·통지·이의 제기(DSA 17·20조).
 *
 * <ul>
 *   <li>관리자가 남의 글·댓글을 지우거나 메시지를 가리거나 계정을 정지하면 {@link #record} — 무엇을, 어떤 사유(코드)로.
 *       당사자에게 알림(MOD_DECISION)이 가고, 알림을 누르면 내용과 이의 제기 화면이 열린다.</li>
 *   <li>이의는 조치 하나에 한 번, 180일 안에. 정지된 사람은 로그인할 수 없어 로그인 실패 응답에 짧은 확인표(appealTicket)를 준다.</li>
 *   <li>재검토 결과(유지·뒤집기)와 메모를 남긴다. 뒤집으면 조치를 되돌리고 결과를 알린다. 본인 조치의 이의는 본인이 못 본다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ModDecisionService {

    public static final List<String> REASONS = List.of("SPAM", "HARASSMENT", "HATE", "VIOLENCE", "SEXUAL", "CHILD_SAFETY",
            "ILLEGAL", "PRIVACY", "IMPERSONATION", "COPYRIGHT", "OTHER");
    static final int APPEAL_DAYS = 180, TEXT_MAX = 2000;
    private static final int REDIS_DB = 15, TICKET_TTL = 3600;

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final RedisApiService redis;
    private final AppealReverser reverser;

    /** 사유 코드 확인 — 없거나 모르는 값이면 OTHER */
    public static String reason(Object v) {
        String r = v == null ? "" : String.valueOf(v).trim().toUpperCase();
        return REASONS.contains(r) ? r : "OTHER";
    }

    /** 조치를 남기고 당사자에게 알린다. 자기 것을 지운 경우(관리자 본인 글)는 남기지 않는다 */
    public Long record(long userSn, String kind, Long targetSn, String reason, String detail, String snapshot, Long admin) {
        if (admin != null && admin == userSn) return null;
        String d = detail == null || detail.isBlank() ? null : detail.strip();
        if (d != null && d.length() > 500) d = d.substring(0, 500);
        String snap = snapshot == null ? null : (snapshot.length() > 300 ? snapshot.substring(0, 299) + "…" : snapshot);
        Long sn = jdbc.queryForObject("""
                INSERT INTO knowra_user.tbl_mod_decision (user_sn, kind, target_sn, reason, detail, snapshot, decided_by)
                VALUES (?,?,?,?,?,?,?) RETURNING decision_sn""", Long.class, userSn, kind, targetSn, reason(reason), d, snap, admin);
        // 계정 정지는 로그인을 못 하니 알림 대신 로그인 화면이 알린다(사유와 이의 제기) — 그래도 기록은 남겨 이의를 받는다
        if (!"SUSPEND".equals(kind)) events.publishEvent(new NotifTriggerEvent(userSn, null, "MOD_DECISION", sn, "DECISION", kind));
        return sn;
    }

    /** 당사자가 보는 조치 한 건 — 본인 것만 */
    public Map<String, Object> view(long decisionSn, long me) {
        Map<String, Object> d = row(decisionSn);
        if (((Number) d.get("userSn")).longValue() != me) throw new IllegalArgumentException("조치를 찾을 수 없어요.");
        d.remove("decidedBy"); d.remove("appealBy");   // 누가 결정했는지는 당사자에게 주지 않는다
        return d;
    }

    /** 이의 제기. params: decisionSn, text */
    @Transactional
    public Map<String, Object> appeal(Map<String, Object> params, long me) {
        long sn = Long.parseLong(String.valueOf(params.get("decisionSn")));
        Map<String, Object> d = row(sn);
        if (((Number) d.get("userSn")).longValue() != me) throw new IllegalArgumentException("조치를 찾을 수 없어요.");
        return submit(sn, d, params.get("text"));
    }

    /** 정지된 사람의 이의 — 로그인 실패 응답의 확인표로. params: ticket, text */
    @Transactional
    public Map<String, Object> appealSuspended(Map<String, Object> params) {
        Object v = redis.getRedis(REDIS_DB, "appeal:tk:" + params.get("ticket"));
        if (v == null) throw new IllegalArgumentException("확인 시간이 지났어요. 다시 로그인해 주세요.");
        long userSn = Long.parseLong(String.valueOf(v));
        Long sn = jdbc.query("SELECT decision_sn FROM knowra_user.tbl_mod_decision WHERE user_sn = ? AND kind = 'SUSPEND' ORDER BY created_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, userSn);
        if (sn == null) throw new IllegalArgumentException("조치를 찾을 수 없어요.");
        return submit(sn, row(sn), params.get("text"));
    }

    /** 정지된 사람의 조치 내용(확인표로) — 이의 화면이 보여 준다 */
    public Map<String, Object> viewSuspended(String ticket) {
        Object v = redis.getRedis(REDIS_DB, "appeal:tk:" + ticket);
        if (v == null) throw new IllegalArgumentException("확인 시간이 지났어요. 다시 로그인해 주세요.");
        long userSn = Long.parseLong(String.valueOf(v));
        Long sn = jdbc.query("SELECT decision_sn FROM knowra_user.tbl_mod_decision WHERE user_sn = ? AND kind = 'SUSPEND' ORDER BY created_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, userSn);
        if (sn == null) throw new IllegalArgumentException("조치를 찾을 수 없어요.");
        Map<String, Object> d = row(sn);
        d.remove("decidedBy"); d.remove("appealBy");
        return d;
    }

    /** 로그인 실패(정지) 응답에 넣을 확인표 — 비밀번호가 맞은 뒤에만 부른다 */
    public String ticketFor(long userSn) {
        String t = UUID.randomUUID().toString().replace("-", "");
        redis.setRedis(REDIS_DB, "appeal:tk:" + t, String.valueOf(userSn), TICKET_TTL);
        return t;
    }

    private Map<String, Object> submit(long sn, Map<String, Object> d, Object textRaw) {
        String text = textRaw == null ? "" : String.valueOf(textRaw).strip();
        if (text.length() < 10) throw new IllegalArgumentException("이의 내용을 10자 이상 써 주세요.");
        if (text.length() > TEXT_MAX) text = text.substring(0, TEXT_MAX);
        if (!"NONE".equals(d.get("appealStat"))) throw new IllegalArgumentException("이미 이의를 냈어요.");
        if (((LocalDateTime) d.get("createdAt")).isBefore(LocalDateTime.now().minusDays(APPEAL_DAYS)))
            throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("조치 후 {0}일이 지나 이의를 낼 수 없어요.", APPEAL_DAYS));
        int n = jdbc.update("UPDATE knowra_user.tbl_mod_decision SET appeal_stat = 'OPEN', appeal_text = ?, appeal_at = now() WHERE decision_sn = ? AND appeal_stat = 'NONE'", text, sn);
        if (n == 0) throw new IllegalArgumentException("이미 이의를 냈어요.");
        return Map.of("appealStat", "OPEN");
    }

    // ── 관리자 ─────────────────────────────────────────────────────────────

    /** 이의 목록 — stat: OPEN(기다림) | DONE(처리함) */
    public Map<String, Object> adminList(String stat) {
        String where = "DONE".equals(stat) ? "d.appeal_stat IN ('UPHELD','REVERSED')" : "d.appeal_stat = 'OPEN'";
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT d.*, u.nick_name FROM knowra_user.tbl_mod_decision d JOIN knowra_user.tbl_user u ON u.user_sn = d.user_sn
                WHERE\s""" + where + " ORDER BY d.appeal_at " + ("DONE".equals(stat) ? "DESC" : "ASC") + " LIMIT 100")) {
            Map<String, Object> m = camel(r);
            m.put("nickName", r.get("nick_name"));
            list.add(m);
        }
        return Map.of("list", list);
    }

    /** 재검토. params: decisionSn, result(UPHELD|REVERSED), note */
    @Transactional
    public Map<String, Object> adminDecide(Map<String, Object> params, long admin) {
        long sn = Long.parseLong(String.valueOf(params.get("decisionSn")));
        String result = String.valueOf(params.get("result"));
        if (!"UPHELD".equals(result) && !"REVERSED".equals(result)) throw new IllegalArgumentException("잘못된 요청이에요.");
        Map<String, Object> d = row(sn);
        if (!"OPEN".equals(d.get("appealStat"))) throw new IllegalArgumentException("기다리는 이의가 아니에요.");
        if (((Number) d.get("userSn")).longValue() == admin) throw new IllegalArgumentException("자기 조치의 이의는 다른 관리자가 봐야 해요.");
        // 처음 결정한 사람이 아닌 관리자가 보는 게 좋지만, 지금은 관리자가 한 명(JwtProvider)이라 막지 않는다 — 막으면 이의를 영영 처리할 수 없다
        String note = params.get("note") == null ? null : String.valueOf(params.get("note")).strip();
        if (note != null && note.length() > 500) note = note.substring(0, 500);
        jdbc.update("UPDATE knowra_user.tbl_mod_decision SET appeal_stat = ?, appeal_by = ?, appeal_note = ?, appeal_done_at = now() WHERE decision_sn = ?", result, admin, note, sn);
        long userSn = ((Number) d.get("userSn")).longValue();
        if ("REVERSED".equals(result)) reverser.reverse(String.valueOf(d.get("kind")), d.get("targetSn") == null ? null : ((Number) d.get("targetSn")).longValue(), userSn, admin);
        events.publishEvent(new NotifTriggerEvent(userSn, null, "REVERSED".equals(result) ? "APPEAL_REVERSED" : "APPEAL_UPHELD", sn, "DECISION", String.valueOf(d.get("kind"))));
        return Map.of("appealStat", result);
    }

    // ── 공통 ─────────────────────────────────────────────────────────────

    private Map<String, Object> row(long sn) {
        List<Map<String, Object>> r = jdbc.queryForList("SELECT * FROM knowra_user.tbl_mod_decision WHERE decision_sn = ?", sn);
        if (r.isEmpty()) throw new IllegalArgumentException("조치를 찾을 수 없어요.");
        return camel(r.get(0));
    }

    private static Map<String, Object> camel(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("decisionSn", r.get("decision_sn"));
        m.put("userSn", r.get("user_sn"));
        m.put("kind", r.get("kind"));
        m.put("targetSn", r.get("target_sn"));
        m.put("reason", r.get("reason"));
        m.put("detail", r.get("detail"));
        m.put("snapshot", r.get("snapshot"));
        m.put("decidedBy", r.get("decided_by"));
        m.put("createdAt", r.get("created_at") instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : r.get("created_at"));
        m.put("appealStat", r.get("appeal_stat"));
        m.put("appealText", r.get("appeal_text"));
        m.put("appealAt", r.get("appeal_at"));
        m.put("appealBy", r.get("appeal_by"));
        m.put("appealNote", r.get("appeal_note"));
        m.put("appealDoneAt", r.get("appeal_done_at"));
        m.put("appealDeadline", (r.get("created_at") instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : LocalDateTime.now()).plusDays(APPEAL_DAYS));
        return m;
    }
}
