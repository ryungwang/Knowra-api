package com.knowra.community.joinq;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 가입 질문 — 승인제 커뮤니티가 신청 때 묻는 질문(최대 3개). 질문은 누구나 본다(신청 전에 읽어야 한다),
 * 고치기는 운영진, 답은 운영진만 본다. 답은 승인·거절이 끝나면 지운다.
 */
@Service
@RequiredArgsConstructor
public class JoinQuestionService {

    public static final int MAX = 3, Q_LEN = 100, A_LEN = 500;
    private final JdbcTemplate jdbc;

    public List<String> list(long commSn) {
        return jdbc.queryForList("SELECT question FROM knowra_community.tbl_comm_join_q WHERE comm_sn = ? ORDER BY ord", String.class, commSn);
    }

    @Transactional
    public List<String> save(long commSn, long me, List<?> raw) {
        String role = jdbc.query("SELECT role FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND user_sn = ? AND stat = 'ACTIVE'",
                rs -> rs.next() ? rs.getString(1) : null, commSn, me);
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) throw new IllegalArgumentException("운영진만 고칠 수 있어요.");
        List<String> qs = new ArrayList<>();
        for (Object o : raw == null ? List.of() : raw) {
            String q = o == null ? "" : String.valueOf(o).trim();
            if (q.isEmpty()) continue;
            if (q.length() > Q_LEN) throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("질문은 {0}자까지예요.", Q_LEN));
            qs.add(q);
        }
        if (qs.size() > MAX) throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("질문은 {0}개까지예요.", MAX));
        jdbc.update("DELETE FROM knowra_community.tbl_comm_join_q WHERE comm_sn = ?", commSn);
        for (int i = 0; i < qs.size(); i++)
            jdbc.update("INSERT INTO knowra_community.tbl_comm_join_q (comm_sn, ord, question) VALUES (?, ?, ?)", commSn, i, qs.get(i));
        return qs;
    }

    /**
     * 신청 전에 부른다 — 질문이 있으면 답이 다 있어야 한다. 정리한 답을 돌려준다(저장은 {@link #store}).
     * 신청이 다른 이유로 막히면(이미 신청함·차단) 답을 남기지 않으려고 둘로 나눴다.
     */
    public List<String> validate(long commSn, Object raw) {
        List<String> qs = list(commSn);
        if (qs.isEmpty()) return List.of();
        List<?> in = raw instanceof List<?> l ? l : List.of();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < qs.size(); i++) {
            String a = i < in.size() && in.get(i) != null ? String.valueOf(in.get(i)).trim() : "";
            if (a.isEmpty()) throw new IllegalArgumentException("가입 질문에 모두 답해 주세요.");
            if (a.length() > A_LEN) throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("답은 {0}자까지예요.", A_LEN));
            out.add(a);
        }
        return out;
    }

    public void store(long commSn, long userSn, List<String> answers) {
        jdbc.update("DELETE FROM knowra_community.tbl_comm_join_ans WHERE comm_sn = ? AND user_sn = ?", commSn, userSn);
        if (answers.isEmpty()) return;
        List<String> qs = list(commSn);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < Math.min(qs.size(), answers.size()); i++)
            jdbc.update("INSERT INTO knowra_community.tbl_comm_join_ans (comm_sn, user_sn, ord, question, answer, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    commSn, userSn, i, qs.get(i), answers.get(i), now);
    }

    /** 가입 신청 목록용 — userSn → [{question, answer}] */
    public Map<Long, List<Map<String, Object>>> answersFor(long commSn) {
        Map<Long, List<Map<String, Object>>> out = new HashMap<>();
        jdbc.query("SELECT user_sn, question, answer FROM knowra_community.tbl_comm_join_ans WHERE comm_sn = ? ORDER BY user_sn, ord", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("question", rs.getString("question")); m.put("answer", rs.getString("answer"));
            out.computeIfAbsent(rs.getLong("user_sn"), k -> new ArrayList<>()).add(m);
        }, commSn);
        return out;
    }

    /** 승인·거절이 끝났다 — 답은 더 쓸 데가 없다 */
    public void clear(long commSn, long userSn) {
        jdbc.update("DELETE FROM knowra_community.tbl_comm_join_ans WHERE comm_sn = ? AND user_sn = ?", commSn, userSn);
    }
}
