package com.knowra.legal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 약관·개인정보 처리방침 동의와 나이 확인.
 *
 * <p>문서의 판(VERSION)은 여기 한 곳에서 정한다 — 화면은 /api/legal/info 로 받아 가고, 문서 본문(화면의 src/legal)의
 * 「시행일」과 같아야 한다. 문서를 고치면 판을 올린다 → 기존 회원은 다음 접속 때 다시 동의 창을 본다(status).
 *
 * <p>최소 나이는 설정값(knowra.legal.min-age, 기본 14)이다. 한국 개인정보보호법은 만 14세 미만이면 법정대리인 동의가
 * 필요해 그 절차 없이 받지 않는다. 다른 나라의 기준(미국 13, EU 일부 16)은 운영 결정이라 설정으로 둔다.
 */
@Service
@RequiredArgsConstructor
public class LegalService {

    public static final String TERMS = "TERMS", PRIVACY = "PRIVACY", AGE = "AGE";
    /** 지금 판 — 문서 시행일. 문서를 바꾸면 같이 올린다 */
    public static final String TERMS_VERSION = "2026-10-16", PRIVACY_VERSION = "2026-10-16";

    private final JdbcTemplate jdbc;

    @Value("${knowra.legal.min-age:14}")
    private int minAge;

    public int minAge() { return minAge; }

    /** 공개 — 화면이 동의 창·문서에 쓴다 */
    public Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("termsVersion", TERMS_VERSION);
        m.put("privacyVersion", PRIVACY_VERSION);
        m.put("minAge", minAge);
        return m;
    }

    /** 이 사람이 지금 판에 동의하지 않은 것 — 비어 있으면 됐다 */
    public List<String> missing(long userSn) {
        Map<String, String> latest = new LinkedHashMap<>();
        jdbc.query("SELECT DISTINCT ON (kind) kind, version FROM knowra_user.tbl_user_consent WHERE user_sn = ? ORDER BY kind, agreed_at DESC",
                rs -> { latest.put(rs.getString(1), rs.getString(2)); }, userSn);
        List<String> need = new ArrayList<>();
        if (!TERMS_VERSION.equals(latest.get(TERMS))) need.add(TERMS);
        if (!PRIVACY_VERSION.equals(latest.get(PRIVACY))) need.add(PRIVACY);
        // 나이는 한 번 확인하면 된다 — 최소 나이를 올렸을 때만 다시(그때의 기준을 VERSION 에 적어 둔다)
        String age = latest.get(AGE);
        if (age == null || Integer.parseInt(age) < minAge) need.add(AGE);
        return need;
    }

    public Map<String, Object> status(long userSn) {
        Map<String, Object> m = info();
        m.put("needs", missing(userSn));
        return m;
    }

    /** 가입 요청에 세 동의가 다 들어 있는가 — 가입 전에 본다(계정을 만들고 나서 거절하지 않게) */
    public void requireAtJoin(Map<String, Object> params) {
        if (!yes(params.get("agreeTerms")) || !yes(params.get("agreePrivacy")))
            throw new IllegalArgumentException("이용약관과 개인정보 처리방침에 동의해야 가입할 수 있어요.");
        if (!yes(params.get("ageConfirm")))
            throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("만 {0}세 이상만 가입할 수 있어요.", minAge));
    }

    /** 동의 기록. kinds 중 지금 판으로 남긴다 */
    public void record(long userSn, List<String> kinds, String ip) {
        for (String k : kinds) {
            String v = switch (k) { case TERMS -> TERMS_VERSION; case PRIVACY -> PRIVACY_VERSION; case AGE -> String.valueOf(minAge); default -> null; };
            if (v != null) jdbc.update("INSERT INTO knowra_user.tbl_user_consent (user_sn, kind, version, ip) VALUES (?,?,?,?)", userSn, k, v, ip);
        }
    }

    /** 다시 동의(문서가 바뀌었거나 예전에 동의 절차 없이 가입한 회원). params: agreeTerms, agreePrivacy, ageConfirm */
    public Map<String, Object> agree(long userSn, Map<String, Object> params, String ip) {
        List<String> need = missing(userSn);
        List<String> ok = new ArrayList<>();
        for (String k : need) {
            boolean y = switch (k) { case TERMS -> yes(params.get("agreeTerms")); case PRIVACY -> yes(params.get("agreePrivacy")); default -> yes(params.get("ageConfirm")); };
            if (!y) {
                if (AGE.equals(k)) throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("만 {0}세 이상만 가입할 수 있어요.", minAge));
                throw new IllegalArgumentException("이용약관과 개인정보 처리방침에 동의해야 가입할 수 있어요.");
            }
            ok.add(k);
        }
        record(userSn, ok, ip);
        return status(userSn);
    }

    /** 내 정보 내려받기(DataExportService)에 넣을 동의 기록 */
    public List<Map<String, Object>> history(long userSn) {
        return jdbc.queryForList("SELECT kind, version, agreed_at FROM knowra_user.tbl_user_consent WHERE user_sn = ? ORDER BY agreed_at", userSn);
    }

    private static boolean yes(Object v) { return v != null && Boolean.parseBoolean(String.valueOf(v)); }
}
