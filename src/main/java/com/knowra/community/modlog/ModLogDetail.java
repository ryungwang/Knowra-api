package com.knowra.community.modlog;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 운영 기록의 상세를 <b>문장이 아니라 값으로</b> 남긴다(JSON) — 화면이 보는 사람의 언어로 문장을 만든다.
 * 예전엔 「공개 범위 공개→비공개」「10월 19일 14:38까지」처럼 한국어 문장을 저장해 다른 언어 화면에도 한국어가 나왔다.
 * 사용자가 쓴 글자(이름·사유·제목)는 그대로 값으로 넣는다. 칸이 300자라 긴 값은 줄인다(JSON 이 잘리면 못 읽는다).
 * 예전 문장 기록은 그대로 두고 화면이 원문으로 보여 준다.
 */
public final class ModLogDetail {
    private ModLogDetail() { }
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static String cut(String s, int n) { return s == null ? null : (s.length() > n ? s.substring(0, n - 1) + "…" : s); }

    private static String json(Map<String, Object> m) {
        try { return MAPPER.writeValueAsString(m); } catch (Exception e) { return null; }
    }

    /** 강퇴 — until 은 절대 시각(ISO, 보는 사람 시간대로 바꿔 보여 준다), null 이면 영구 */
    public static String ban(LocalDateTime until, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("until", until == null ? null : until.atZone(ZoneId.systemDefault()).toInstant().toString());
        if (reason != null && !reason.isBlank()) m.put("reason", cut(reason, 200));
        return json(m);
    }

    /** 커뮤니티 정보 바꾸기 — 바뀐 칸 목록. f: name(from,to)·desc·privacy(from,to)·lang(to)·logo·logoRemoved·banner·bannerRemoved */
    public static final class Settings {
        private final List<Map<String, Object>> changes = new ArrayList<>();
        public Settings add(String f) { return add(f, null, null); }
        public Settings add(String f, Object from, Object to) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("f", f);
            if (from != null) c.put("from", from instanceof String s ? cut(s, 50) : from);
            if (to != null) c.put("to", to instanceof String s ? cut(s, 50) : to);
            changes.add(c);
            return this;
        }
        public boolean isEmpty() { return changes.isEmpty(); }
        public String json() { return ModLogDetail.json(Map.of("settings", changes)); }
    }

    public static String count(int n) { return json(Map.of("count", n)); }

    public static String holdDays(int days) { return json(Map.of("days", days)); }

    public static String reports(String kind, int n) { return json(Map.of("kind", kind, "count", n)); }

    /** 사이트 관리자 기록(audit log)도 같은 방식 — 문장 대신 값만 두고 화면이 언어별로 만든다 */
    public static String of(Map<String, Object> m) { return json(m); }
}
