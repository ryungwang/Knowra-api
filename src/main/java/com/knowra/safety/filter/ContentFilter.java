package com.knowra.safety.filter;

import com.knowra.cmm.i18n.Msg;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.user.repository.TblUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 글·댓글·DM 저장 전 검사. 걸리면 <b>이유 문장</b>을 돌려주고(저장 안 함), 통과면 null.
 *
 * <ol>
 *   <li><b>금칙어</b> — 관리자 목록. 소문자·공백·기호를 지운 뒤 비교한다.</li>
 *   <li><b>같은 내용 반복</b> — 20자 이상 같은 내용을 10분 안에 또 올리면 막는다(복붙 도배).
 *       짧은 말(「감사합니다」)은 여러 곳에 똑같이 쓰는 게 정상이라 보지 않는다. 수정은 보지 않는다.</li>
 *   <li><b>가입 첫날 링크</b> — 스팸 계정은 대개 막 만든 계정이 링크를 뿌린다. 첫 24시간은
 *       댓글·DM 링크 2개, 글 5개까지. 하루 지나면 풀린다.</li>
 * </ol>
 * 관리자는 2·3 을 건너뛴다(공지에 링크를 많이 넣는다).
 */
@Service
@RequiredArgsConstructor
public class ContentFilter {

    /** TEXT = 이름·소개·제목 같은 짧은 글 — 금칙어만 본다(링크 개수·같은 내용 반복은 글·댓글·DM 의 도배 대책이다) */
    public enum Kind { POST, CMT, MSG, TEXT }

    private static final int REDIS_DB = 15;
    private static final int DUP_MIN_LEN = 20;
    private static final int DUP_WINDOW_SEC = 600;
    private static final long CACHE_MS = 30_000;
    private static final Pattern LINK = Pattern.compile("(https?://|www\\.)[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);
    /** 비교 전에 지우는 것: 공백, 문장부호·기호, 보이지 않는 글자. 한글·영문·숫자만 남긴다. */
    private static final Pattern NOISE = Pattern.compile("[^\\p{IsHangul}\\p{IsAlphabetic}\\p{IsDigit}]+");

    private final JdbcTemplate jdbc;
    private final RedisApiService redis;
    private final TblUserRepository userRepository;

    private volatile List<String> words = List.of();
    private volatile long loadedAt = 0;

    public String check(Long userSn, boolean isAdmin, Kind kind, String text, boolean isEdit) {
        if (text == null || text.isBlank()) return null;
        String norm = normalize(text);

        for (String w : banned()) {
            if (norm.contains(w)) return Msg.t("사용할 수 없는 표현이 들어 있어요(「{0}」). 고쳐서 다시 올려 주세요.", w);
        }
        if (userSn == null || isAdmin || kind == Kind.TEXT) return null;

        int links = 0;
        Matcher m = LINK.matcher(text);
        while (m.find()) links++;
        int maxLinks = kind == Kind.POST ? 5 : 2;
        if (links > maxLinks && isNewAccount(userSn))
            return Msg.t("가입 첫날에는 링크를 {0}개까지만 넣을 수 있어요. 내일부터는 제한이 풀려요.", maxLinks);

        if (!isEdit && norm.length() >= DUP_MIN_LEN) {
            String key = "dup:" + kind + ":" + userSn + ":" + sha1(norm);
            try {
                if (!redis.setIfAbsent(REDIS_DB, key, "1", DUP_WINDOW_SEC))
                    return "방금 올린 것과 같은 내용이에요. 같은 글을 여러 번 올리지 말아 주세요.";
            } catch (Exception ignored) { /* Redis 가 없으면 이 검사만 건너뛴다 */ }
        }
        return null;
    }

    /** 관리자 목록을 고친 뒤 바로 반영. */
    public void invalidate() { loadedAt = 0; }

    public static String normalize(String s) {
        String noTags = s.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ");
        return NOISE.matcher(noTags.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private List<String> banned() {
        long now = System.currentTimeMillis();
        if (now - loadedAt > CACHE_MS) {
            try {
                words = jdbc.queryForList("SELECT WORD FROM knowra_com.tbl_banned_word", String.class)
                        .stream().map(ContentFilter::normalize).filter(w -> w.length() >= 2).toList();
                loadedAt = now;
            } catch (Exception ignored) { /* DB 가 잠깐 안 되면 이전 목록으로 */ }
        }
        return words;
    }

    private boolean isNewAccount(long userSn) {
        return userRepository.findById(userSn)
                .map(u -> u.getFrstCrtDt() != null && u.getFrstCrtDt().isAfter(LocalDateTime.now().minusHours(24)))
                .orElse(false);
    }

    private static String sha1(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }
}
