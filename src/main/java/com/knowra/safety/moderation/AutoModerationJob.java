package com.knowra.safety.moderation;

import com.knowra.safety.service.ReportAutoHide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 새 글·댓글·수정본을 1분마다 훑어 자동 검수({@link TextModerator})에 보낸다. 걸리면 신고함에 올린다.
 *
 * <p><b>왜 훑나(이벤트가 아니라):</b> 글·댓글을 쓰고 고치는 길이 여러 곳(JSON·폼·예약 발행·수정)이라 하나라도 빠뜨리면
 * 그 길로 들어온 글은 영영 안 본다. 수정 시각으로 훑으면 길과 상관없이 다 잡힌다. 같은 내용(해시)은 다시 보내지 않는다.
 * 방금 쓴 것은 30초 기다린다 — 커밋이 늦게 끝난 쓰기가 커서 뒤로 밀려 빠지지 않게.
 *
 * <p>보내는 것: 글 제목·본문, 댓글 본문의 글자만. 사이트 관리자 글, 사진, DM 은 보내지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "knowra.moderation.provider", havingValue = "openai")
public class AutoModerationJob {

    static final int BATCH = 32;
    static final int MAX_CHARS = 4000;

    private final JdbcTemplate jdbc;
    private final TextModerator moderator;
    private final ReportAutoHide autoHide;

    record Item(String kind, long sn, Long commSn, String text, Timestamp ts) {}

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void run() {
        for (String kind : List.of("POST", "CMT")) {
            try { sweep(kind); }
            catch (Exception e) { log.warn("자동 검수 {} 건너뜀 — 다음 차례에 다시: {}", kind, e.toString()); }
        }
    }

    void sweep(String kind) {
        Timestamp cursor = jdbc.query("SELECT upto FROM knowra_com.tbl_auto_mod_cursor WHERE kind = ?", rs -> rs.next() ? rs.getTimestamp(1) : null, kind);
        if (cursor == null) {   // 처음 켰다 — 지금부터 본다(예전 글을 한꺼번에 밖으로 보내지 않는다)
            jdbc.update("INSERT INTO knowra_com.tbl_auto_mod_cursor (kind, upto) VALUES (?, now()) ON CONFLICT (kind) DO NOTHING", kind);
            return;
        }
        List<Item> items = "POST".equals(kind) ? jdbc.query("""
                SELECT p.post_sn, p.comm_sn, p.post_ttl, p.post_cntnt, COALESCE(p.mdfcn_dt, p.frst_crt_dt) AS ts
                FROM knowra_post.tbl_post p
                WHERE p.actvtn_yn = 'Y' AND p.stat = 'ACTIVE' AND p.user_sn NOT IN (SELECT user_sn FROM knowra_user.tbl_user WHERE site_role = 'ADMIN')
                  AND COALESCE(p.mdfcn_dt, p.frst_crt_dt) > ? AND COALESCE(p.mdfcn_dt, p.frst_crt_dt) <= now() - interval '30 seconds'
                ORDER BY ts LIMIT ?""",
                (rs, i) -> new Item(kind, rs.getLong(1), (Long) rs.getObject(2, Long.class), text(rs.getString(3), rs.getString(4)), rs.getTimestamp(5)),
                cursor, BATCH)
                : jdbc.query("""
                SELECT c.post_cmt_sn, p.comm_sn, c.cmt_cntnt, COALESCE(c.mdfcn_dt, c.frst_crt_dt) AS ts
                FROM knowra_post.tbl_post_cmt c JOIN knowra_post.tbl_post p ON p.post_sn = c.post_sn
                WHERE c.actvtn_yn = 'Y' AND c.stat = 'ACTIVE' AND c.user_sn NOT IN (SELECT user_sn FROM knowra_user.tbl_user WHERE site_role = 'ADMIN')
                  AND COALESCE(c.mdfcn_dt, c.frst_crt_dt) > ? AND COALESCE(c.mdfcn_dt, c.frst_crt_dt) <= now() - interval '30 seconds'
                ORDER BY ts LIMIT ?""",
                (rs, i) -> new Item(kind, rs.getLong(1), (Long) rs.getObject(2, Long.class), text(null, rs.getString(3)), rs.getTimestamp(4)),
                cursor, BATCH);
        if (items.isEmpty()) return;

        List<Item> send = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (Item it : items) {
            if (it.text().isBlank()) continue;
            String h = sha1(it.text());
            String seen = jdbc.query("SELECT text_hash FROM knowra_com.tbl_auto_mod WHERE target_kind = ? AND target_sn = ?", rs -> rs.next() ? rs.getString(1) : null, kind, it.sn());
            if (h.equals(seen)) continue;   // 수정 시각만 바뀌고 글은 그대로
            send.add(it);
            hashes.add(h);
        }
        // 실패하면 예외 — 커서를 옮기지 않아 다음 차례에 같은 것을 다시 본다
        List<TextModerator.Verdict> verdicts = moderator.check(send.stream().map(Item::text).toList());
        for (int i = 0; i < send.size(); i++) {
            Item it = send.get(i);
            TextModerator.Verdict v = verdicts.get(i);
            jdbc.update("""
                    INSERT INTO knowra_com.tbl_auto_mod (target_kind, target_sn, text_hash, flagged, checked_at) VALUES (?, ?, ?, ?, now())
                    ON CONFLICT (target_kind, target_sn) DO UPDATE SET text_hash = EXCLUDED.text_hash, flagged = EXCLUDED.flagged, checked_at = now()""",
                    kind, it.sn(), hashes.get(i), v.flagged());
            if (v.flagged()) report(it, v);
        }
        jdbc.update("UPDATE knowra_com.tbl_auto_mod_cursor SET upto = ? WHERE kind = ?", items.getLast().ts(), kind);
    }

    /** 신고함에 올린다 — 이미 열린 자동 신고가 있으면 그대로 둔다 */
    private void report(Item it, TextModerator.Verdict v) {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_user.tbl_report WHERE reporter_sn IS NULL AND target_kind = ? AND target_sn = ? AND stat = 'OPEN'",
                Integer.class, it.kind(), it.sn());
        if (open != null && open > 0) return;
        String reason = reasonOf(v);
        jdbc.update("""
                INSERT INTO knowra_user.tbl_report (reporter_sn, target_kind, target_sn, reason, detail, comm_sn, comm_stat)
                VALUES (NULL, ?, ?, ?, ?, ?, ?)""",
                it.kind(), it.sn(), reason, detailOf(v), it.commSn(), it.commSn() == null ? null : "OPEN");
        autoHide.afterReport(it.kind(), it.sn(), reason);   // 아동 안전만 바로 가린다 — 나머지는 문턱에 세지 않는다
    }

    /** OpenAI 분류 → 신고 사유. 아동 대상 성적 내용은 점수와 상관없이 아동 안전 */
    static String reasonOf(TextModerator.Verdict v) {
        if (v.flags().stream().anyMatch(f -> f.category().equals("sexual/minors"))) return "CHILD_SAFETY";
        String top = v.flags().getFirst().category();
        if (top.startsWith("sexual")) return "SEXUAL";
        if (top.startsWith("illicit")) return "ILLEGAL";
        if (top.startsWith("harassment") || top.startsWith("hate") || top.startsWith("violence")) return "ABUSE";
        return "OTHER";   // self-harm 등
    }

    /** 「AI · harassment 93% · hate 81%」 — 관리자가 무엇 때문에 올라왔는지 본다 */
    static String detailOf(TextModerator.Verdict v) {
        String s = "AI · " + v.flags().stream().limit(4)
                .map(f -> f.category() + " " + Math.round(f.score() * 100) + "%").collect(Collectors.joining(" · "));
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    static String text(String title, String body) {
        String b = body == null ? "" : body.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
        String t = (title == null || title.isBlank() ? "" : title.trim() + "\n") + b;
        return t.length() > MAX_CHARS ? t.substring(0, MAX_CHARS) : t;
    }

    private static String sha1(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
