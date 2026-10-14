package com.knowra.mention;

import com.knowra.cmm.event.NotifTriggerEvent;
import com.knowra.common.entity.QTblComFile;
import com.knowra.post.service.PostQuery;
import com.knowra.safety.entity.QTblUserBlock;
import com.knowra.user.entity.QTblUser;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.knowra.post.service.PostQuery.POST;

/**
 * @멘션. 글·댓글 본문에서 {@code @닉네임}을 찾아 그 사람에게 MENTION 알림을 보낸다.
 *
 * <p>보내지 않는 경우: 없는 닉네임 · 정지 계정 · 그 글을 볼 수 없는 사람(비공개 커뮤니티 비멤버 등 —
 * 알림 문구로 글 존재가 새면 안 된다) · 같은 동작으로 이미 알림을 받은 사람(글 주인 COMMENT·
 * 부모 댓글 REPLY) · 자기 자신·차단 관계(리스너가 거른다). 한 번에 최대 {@value #MAX_PER_TEXT}명.
 */
@Service
@RequiredArgsConstructor
public class MentionService {

    /** 앞이 글자나 @ 가 아닐 때만 — 이메일(a@b.com)을 멘션으로 읽지 않게. 닉네임 허용 문자와 같게. */
    static final Pattern MENTION = Pattern.compile("(?<![0-9A-Za-z가-힣._@-])@([0-9A-Za-z가-힣._-]{2,30})");
    public static final int MAX_PER_TEXT = 10;
    /** "@deer님", "@deer에게" 처럼 띄우지 않고 붙는 말. 닉네임에 한글이 들어갈 수 있어 정규식만으로는 못 가른다. */
    static final List<String> TAILS = List.of("님께서", "에게서", "한테서", "님께", "님이", "님은", "님을", "님의", "에게", "한테", "께서", "님", "씨", "이", "가", "은", "는", "을", "를", "의", "도", "아", "야", "께");

    /** 멘션 하나가 가리킬 수 있는 닉네임 후보 — 긴 것부터. 실제 있는 것 중 가장 긴 걸 고른다. */
    static List<String> variants(String token) {
        List<String> out = new ArrayList<>();
        out.add(token);
        for (String t : TAILS) {
            if (token.length() > t.length() + 1 && token.endsWith(t)) out.add(token.substring(0, token.length() - t.length()));
        }
        out.sort(Comparator.comparingInt(String::length).reversed());
        return out.stream().distinct().toList();
    }

    @PersistenceContext
    private EntityManager em;
    private final PostQuery postQuery;
    private final ApplicationEventPublisher events;

    /** 본문(HTML 이어도 된다)에서 닉네임 목록. 순서 유지·중복 제거. */
    public static List<String> extract(String text) {
        if (text == null || text.isEmpty()) return List.of();
        String plain = text.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher m = MENTION.matcher(plain);
        while (m.find() && out.size() < MAX_PER_TEXT) {
            out.add(m.group(1).replaceAll("[._-]+$", ""));   // 문장 끝 마침표 등은 떼어 낸다
        }
        out.removeIf(s -> s.length() < 2);
        return new ArrayList<>(out);
    }

    /**
     * @param text      새 본문
     * @param before    수정 전 본문(새 글·새 댓글이면 null) — 이미 있던 멘션은 다시 알리지 않는다
     * @param skip      이 동작으로 이미 알림을 받은 사람
     */
    public void notify(long postSn, long senderSn, String text, String before, Collection<Long> skip) {
        List<String> tokens = extract(text);
        if (tokens.isEmpty()) return;
        List<String> beforeTokens = before == null ? List.of() : extract(before);
        tokens.removeAll(beforeTokens);
        if (tokens.isEmpty()) return;
        Set<String> all = new HashSet<>();
        tokens.forEach(t -> all.addAll(variants(t)));
        QTblUser u = QTblUser.tblUser;
        Map<String, Long> byNick = new HashMap<>();
        new JPAQueryFactory(em).select(u.nickName, u.userSn).from(u)
                .where(u.nickName.in(all), u.actvtnYn.eq("Y"), u.status.ne("S")).fetch()
                .forEach(r -> byNick.put(r.get(u.nickName), r.get(u.userSn)));
        LinkedHashSet<Long> targets = new LinkedHashSet<>();
        for (String t : tokens) {
            for (String v : variants(t)) { Long sn = byNick.get(v); if (sn != null) { targets.add(sn); break; } }
        }
        Set<Long> skipSet = new HashSet<>(skip == null ? List.of() : skip);
        for (Long t : targets) {
            if (t == senderSn || skipSet.contains(t)) continue;
            boolean canSee = postQuery.base().where(POST.postSn.eq(postSn), PostQuery.alive(), PostQuery.visibleTo(t)).fetchFirst() != null;
            if (!canSee) continue;
            events.publishEvent(new NotifTriggerEvent(t, senderSn, "MENTION", postSn, "POST"));
        }
    }

    /** 입력창 자동완성 — 닉네임·이름 앞부분 일치 우선, 최대 6명. 나·정지·차단(양방향) 제외. */
    public List<Map<String, Object>> search(String q, long me) {
        String s = q == null ? "" : q.trim().replaceFirst("^@", "");
        if (s.isEmpty() || s.length() > 30) return List.of();
        QTblUser u = QTblUser.tblUser;
        QTblComFile pfp = new QTblComFile("pfpFile");
        QTblUserBlock b = QTblUserBlock.tblUserBlock;
        List<Tuple> rows = new JPAQueryFactory(em)
                .select(u.userSn, u.nickName, u.name, pfp.atchFilePathNm, pfp.strgFileNm, pfp.atchFileExtnNm, pfp.fileKey)
                .from(u).leftJoin(u.pfp, pfp)
                .where(u.actvtnYn.eq("Y"), u.status.eq("Y"), u.userSn.ne(me),
                        u.nickName.startsWithIgnoreCase(s).or(u.name.startsWithIgnoreCase(s)).or(u.nickName.containsIgnoreCase(s)),
                        u.userSn.notIn(JPAExpressions.select(b.blockedSn).from(b).where(b.userSn.eq(me))),
                        u.userSn.notIn(JPAExpressions.select(b.userSn).from(b).where(b.blockedSn.eq(me))))
                .orderBy(u.nickName.asc())
                .limit(20).fetch();
        String low = s.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nickName", t.get(u.nickName)); m.put("name", t.get(u.name));
            String path = t.get(pfp.atchFilePathNm);
            m.put("pfpUrl", com.knowra.cmm.storage.StorageUrls.of(path, t.get(pfp.strgFileNm), t.get(pfp.atchFileExtnNm), t.get(pfp.fileKey)));
            list.add(m);
        }
        // 앞부분이 맞는 사람이 위로
        list.sort(Comparator.comparingInt(m -> String.valueOf(m.get("nickName")).toLowerCase(Locale.ROOT).startsWith(low) ? 0 : 1));
        return list.subList(0, Math.min(6, list.size()));
    }
}
