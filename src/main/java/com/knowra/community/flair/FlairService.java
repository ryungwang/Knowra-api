package com.knowra.community.flair;

import com.knowra.community.modlog.ModLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 커뮤니티 말머리 — 읽기는 누구나(글 목록에 보이는 이름일 뿐), 고치기는 운영진. */
@Service
@RequiredArgsConstructor
public class FlairService {

    public static final int MAX = 10;
    /** 화면이 아는 색 이름만 — 임의 값을 그대로 스타일에 넣지 않는다 */
    public static final Set<String> COLORS = Set.of("gray", "blue", "green", "orange", "pink", "purple", "red", "teal");

    private final JdbcTemplate jdbc;
    private final ModLogService modLog;

    public List<Map<String, Object>> list(long commSn) {
        return jdbc.queryForList("SELECT flair_sn AS \"flairSn\", name, color FROM knowra_community.tbl_comm_flair WHERE comm_sn = ? ORDER BY ord, flair_sn", commSn);
    }

    /** 이 커뮤니티의 말머리면 그대로, 아니면 null — 글에 달 때 남의 커뮤니티 말머리를 못 달게 */
    public Long validFor(Object raw, Long commSn) {
        if (raw == null || commSn == null || String.valueOf(raw).isBlank() || "null".equals(String.valueOf(raw))) return null;
        long sn;
        try { sn = Long.parseLong(String.valueOf(raw)); } catch (NumberFormatException e) { return null; }
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_community.tbl_comm_flair WHERE flair_sn = ? AND comm_sn = ?", Long.class, sn, commSn);
        return n != null && n > 0 ? sn : null;
    }

    /** 통째로 바꾸기. items: [{flairSn?, name, color}] — 순서대로. 빠진 말머리는 지우고 그걸 단 글은 말머리 없음 */
    @Transactional
    public List<Map<String, Object>> save(long commSn, List<?> items, long me) {
        String role = jdbc.query("SELECT role FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND user_sn = ? AND stat = 'ACTIVE'",
                rs -> rs.next() ? rs.getString(1) : null, commSn, me);
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) throw new IllegalArgumentException("운영진만 고칠 수 있어요.");
        List<Map<String, Object>> next = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Object o : items == null ? List.of() : items) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String name = String.valueOf(m.get("name") == null ? "" : m.get("name")).trim();
            if (name.isEmpty()) continue;
            if (name.length() > 20) throw new IllegalArgumentException("말머리는 20자까지예요.");
            if (!names.add(name)) throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("같은 이름의 말머리가 있어요: {0}", name));
            String color = COLORS.contains(String.valueOf(m.get("color"))) ? String.valueOf(m.get("color")) : "gray";
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("flairSn", m.get("flairSn") == null ? null : validFor(m.get("flairSn"), commSn));
            x.put("name", name); x.put("color", color);
            next.add(x);
        }
        if (next.size() > MAX) throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("말머리는 {0}개까지예요.", MAX));
        List<Long> keep = next.stream().map(x -> (Long) x.get("flairSn")).filter(java.util.Objects::nonNull).toList();
        for (Map<String, Object> old : list(commSn)) {
            long sn = ((Number) old.get("flairSn")).longValue();
            if (!keep.contains(sn)) {
                jdbc.update("UPDATE knowra_post.tbl_post SET flair_sn = NULL WHERE flair_sn = ?", sn);
                jdbc.update("DELETE FROM knowra_community.tbl_comm_flair WHERE flair_sn = ?", sn);
            }
        }
        int ord = 0;
        for (Map<String, Object> x : next) {
            if (x.get("flairSn") != null)
                jdbc.update("UPDATE knowra_community.tbl_comm_flair SET name = ?, color = ?, ord = ? WHERE flair_sn = ?", x.get("name"), x.get("color"), ord, x.get("flairSn"));
            else
                jdbc.update("INSERT INTO knowra_community.tbl_comm_flair (comm_sn, name, color, ord) VALUES (?, ?, ?, ?)", commSn, x.get("name"), x.get("color"), ord);
            ord++;
        }
        modLog.record(commSn, me, ModLogService.FLAIRS, null, null, com.knowra.community.modlog.ModLogDetail.count(next.size()));
        return list(commSn);
    }
}
