package com.knowra.search.service;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.common.entity.QTblComFile;
import com.knowra.common.entity.QTblTag;
import com.knowra.community.entity.QTblComm;
import com.knowra.post.entity.PostDTO;
import com.knowra.post.entity.QTblPostTag;
import com.knowra.post.service.PostQuery;
import com.knowra.safety.entity.QTblUserBlock;
import com.knowra.user.entity.QTblUser;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static com.knowra.post.service.PostQuery.POST;

/**
 * 통합 검색 — 글·커뮤니티·사용자·태그를 한 번에.
 *
 * <p>탐색 화면이 그동안 가짜 데이터로 검색하는 척을 하고 있었다(헤더 검색창이 거기로 보냈다).
 * 전문 검색 엔진 없이 LIKE 로 간다 — 지금 규모(글 수백)에서는 충분하고, 커지면 이 클래스만 바꾼다.
 * 검색어가 비어 있으면 인기 태그만 돌려준다(탐색 화면의 기본 상태).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    @PersistenceContext
    private EntityManager em;

    private final PostQuery postQuery;

    private static final int LIMIT_POSTS = 20;
    private static final int LIMIT_OTHER = 10;

    /**
     * 검색 옵션. 값이 이상하면 기본값으로 — 검색은 "틀린 옵션" 때문에 실패하면 안 된다.
     * ⚠️ {@code Set.of(..).contains(null)} 은 false 가 아니라 NPE 다 — 그래서 null 을 먼저 거른다.
     */
    public record Options(String type, String sort, String period, Long commSn, int page) {
        public static Options of(String type, String sort, String period, Long commSn, Integer page) {
            return new Options(
                    type != null && Set.of("posts", "communities", "users", "tags").contains(type) ? type : "all",
                    sort != null && Set.of("latest", "popular").contains(sort) ? sort : "relevance",
                    period != null && Set.of("day", "week", "month", "year").contains(period) ? period : "all",
                    commSn, page == null || page < 0 ? 0 : Math.min(page, 50));
        }
    }

    public ResultVO search(String rawQuery, Long me) { return search(rawQuery, me, Options.of(null, null, null, null, 0)); }

    public ResultVO search(String rawQuery, Long me, Options o) {
        ResultVO resultVO = new ResultVO();
        try {
            String q = rawQuery == null ? "" : rawQuery.trim().replaceFirst("^#", "");
            if (q.isBlank()) {
                resultVO.putResult("popularTags", popularTags());
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
                return resultVO;
            }
            List<String> tokens = tokens(q);
            boolean all = "all".equals(o.type());
            // 글 다음 쪽만 부를 때(page>0)는 나머지 묶음을 다시 찾지 않는다
            if (all || "posts".equals(o.type())) {
                PostPage pp = searchPosts(q, tokens, me, o);
                resultVO.putResult("posts", pp.items());
                resultVO.putResult("postsHasMore", pp.hasMore());
            }
            if (o.page() == 0) {
                if (all || "communities".equals(o.type())) resultVO.putResult("communities", searchCommunities(tokens, me));
                if (all || "users".equals(o.type()))       resultVO.putResult("users", searchUsers(tokens, me));
                if (all || "tags".equals(o.type()))        resultVO.putResult("tags", searchTags(tokens));
            }
            resultVO.putResult("tokens", tokens);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /**
     * 자동완성 — 헤더 검색창이 타이핑마다 부른다. 가볍게: 태그 5 · 커뮤니티 3 · 사용자 3 · 글 제목 4.
     * 글은 제목만 본다(본문 LIKE 는 타이핑마다 돌리기엔 비싸다).
     */
    public ResultVO suggest(String rawQuery, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            String q = rawQuery == null ? "" : rawQuery.trim().replaceFirst("^#", "");
            if (q.isBlank()) { resultVO.setResultCode(ResponseCode.SUCCESS.getCode()); return resultVO; }
            List<String> tokens = tokens(q);
            BooleanExpression titleAll = null;
            for (String t : tokens) titleAll = titleAll == null ? POST.postTtl.containsIgnoreCase(t) : titleAll.and(POST.postTtl.containsIgnoreCase(t));
            List<Map<String, Object>> posts = new ArrayList<>();
            for (Tuple t : postQuery.base().where(PostQuery.alive(), PostQuery.visibleTo(me), titleAll)
                    .orderBy(POST.likeCnt.desc(), POST.frstCrtDt.desc()).limit(4).fetch()) {
                PostDTO d = postQuery.toDto(t);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("postSn", d.getPostSn()); m.put("postTtl", d.getPostTtl()); m.put("commDsplNm", d.getCommDsplNm());
                posts.add(m);
            }
            resultVO.putResult("tags", searchTags(tokens).stream().limit(5).toList());
            resultVO.putResult("communities", searchCommunities(tokens, me).stream().limit(3).toList());
            resultVO.putResult("users", searchUsers(tokens, me).stream().limit(3).toList());
            resultVO.putResult("posts", posts);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /** 공백으로 나눈 낱말(최대 5개, 중복 제거). "코인 투자" 는 두 낱말이 <b>모두</b> 들어간 것을 찾는다. */
    static List<String> tokens(String q) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String t : q.split("\\s+")) { String x = t.replaceFirst("^#", "").trim(); if (!x.isEmpty()) set.add(x); }
        return set.stream().limit(5).toList();
    }

    record PostPage(List<PostDTO> items, boolean hasMore) {}

    /**
     * 글 검색. 낱말마다 (제목 | 본문 | 태그) 중 하나에 들어 있어야 한다(AND).
     *
     * <p>정렬: latest 는 DB 에서 바로 페이지를 자른다. relevance·popular 는 최근 매칭 글
     * {@value #CANDIDATES}개를 후보로 받아 점수를 매긴 뒤 자른다 — 전문 검색 엔진이 없으니
     * 관련도를 SQL 로 짜기보다 이쪽이 단순하고, 지금 규모(글 수백)에선 후보 안에 다 들어온다.
     * 글이 수만 개가 되면 이 창 밖의 오래된 글이 관련도 정렬에서 빠진다 — 그때 엔진을 붙인다.
     */
    private PostPage searchPosts(String q, List<String> tokens, Long me, Options o) {
        QTblPostTag qPostTag = QTblPostTag.tblPostTag;
        QTblTag     qTag     = QTblTag.tblTag;
        List<BooleanExpression> where = new ArrayList<>(List.of(PostQuery.alive(), PostQuery.visibleTo(me)));
        for (String t : tokens) {
            BooleanExpression byTag = POST.postSn.in(
                    JPAExpressions.select(qPostTag.postSn).from(qPostTag)
                            .join(qTag).on(qPostTag.tagSn.eq(qTag.tagSn))
                            .where(qTag.tagNm.containsIgnoreCase(t)));
            where.add(POST.postTtl.containsIgnoreCase(t).or(POST.postCntnt.containsIgnoreCase(t)).or(byTag));
        }
        LocalDateTime since = switch (o.period()) {
            case "day" -> LocalDateTime.now().minusDays(1);
            case "week" -> LocalDateTime.now().minusWeeks(1);
            case "month" -> LocalDateTime.now().minusMonths(1);
            case "year" -> LocalDateTime.now().minusYears(1);
            default -> null;
        };
        if (since != null) where.add(POST.frstCrtDt.after(since));
        if (o.commSn() != null) where.add(POST.commSn.eq(o.commSn()));
        BooleanExpression[] w = where.toArray(new BooleanExpression[0]);

        int from = o.page() * LIMIT_POSTS;
        if ("latest".equals(o.sort())) {
            List<Tuple> rows = postQuery.base().where(w).orderBy(POST.frstCrtDt.desc())
                    .offset(from).limit(LIMIT_POSTS + 1).fetch();
            boolean more = rows.size() > LIMIT_POSTS;
            return new PostPage(postQuery.decorate(more ? rows.subList(0, LIMIT_POSTS) : rows, me), more);
        }

        List<PostDTO> cands = postQuery.decorate(
                postQuery.base().where(w).orderBy(POST.frstCrtDt.desc()).limit(CANDIDATES).fetch(), me);
        String phrase = q.toLowerCase(Locale.ROOT);
        Comparator<PostDTO> cmp = "popular".equals(o.sort())
                ? Comparator.comparingDouble(SearchService::popularity)
                : Comparator.comparingDouble((PostDTO d) -> relevance(d, phrase, tokens));
        cands.sort(cmp.reversed().thenComparing(PostDTO::getFrstCrtDt, Comparator.nullsLast(Comparator.reverseOrder())));
        int to = Math.min(cands.size(), from + LIMIT_POSTS);
        List<PostDTO> page = from >= cands.size() ? List.of() : cands.subList(from, to);
        return new PostPage(new ArrayList<>(page), to < cands.size());
    }

    private static final int CANDIDATES = 300;

    /** 제목 > 태그 > 본문. 검색어 전체가 제목에 그대로 있으면 크게 가산, 최근 글엔 조금. */
    static double relevance(PostDTO d, String phrase, List<String> tokens) {
        String title = d.getPostTtl() == null ? "" : d.getPostTtl().toLowerCase(Locale.ROOT);
        String body  = d.getPostCntnt() == null ? "" : d.getPostCntnt().toLowerCase(Locale.ROOT);
        List<String> tags = d.getTagNms() == null ? List.of() : d.getTagNms().stream().map(x -> x.toLowerCase(Locale.ROOT)).toList();
        double sc = title.contains(phrase) ? 8 : 0;
        for (String raw : tokens) {
            String t = raw.toLowerCase(Locale.ROOT);
            if (title.contains(t)) sc += 5;
            if (tags.contains(t)) sc += 4; else if (tags.stream().anyMatch(x -> x.contains(t))) sc += 2;
            if (body.contains(t)) sc += 1;
        }
        if (d.getFrstCrtDt() != null) {
            long days = java.time.Duration.between(d.getFrstCrtDt(), LocalDateTime.now()).toDays();
            sc += Math.max(0, 3 - days / 30.0);
        }
        return sc;
    }

    static double popularity(PostDTO d) { return d.getLikeCnt() * 3.0 + d.getCmtCnt() * 4.0 + d.getViewCnt() * 0.05; }

    /** 비공개(private) 커뮤니티는 멤버에게만. 승인제(restricted)는 이름이 보여야 신청할 수 있다. */
    private List<Map<String, Object>> searchCommunities(List<String> tokens, Long me) {
        QTblComm    qComm = QTblComm.tblComm;
        QTblComFile qLogo = new QTblComFile("logoFile");
        BooleanExpression discoverable = qComm.prvcyStng.ne("private");
        if (me != null) {
            discoverable = discoverable.or(com.knowra.common.CommunityQueryHelper.accessCondition(qComm, me));
        }
        List<Tuple> rows = new JPAQueryFactory(em)
                .select(qComm.commSn, qComm.commNm, qComm.commDsplNm, qComm.commDesc, qComm.memberCnt, qComm.prvcyStng, qComm.logoFile)
                .from(qComm)
                .leftJoin(qComm.logoFile, qLogo)
                .where(qComm.actvtnYn.eq("Y"), qComm.stat.eq("Y"), discoverable, allTokens(tokens, t ->
                       qComm.commDsplNm.containsIgnoreCase(t)
                               .or(qComm.commNm.containsIgnoreCase(t))
                               .or(qComm.commDesc.containsIgnoreCase(t))))
                .orderBy(qComm.memberCnt.desc())
                .limit(LIMIT_OTHER)
                .fetch();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("commSn",     t.get(qComm.commSn));
            m.put("commNm",     t.get(qComm.commNm));
            m.put("commDsplNm", t.get(qComm.commDsplNm));
            m.put("commDesc",   t.get(qComm.commDesc));
            m.put("memberCnt",  t.get(qComm.memberCnt));
            m.put("prvcyStng",  t.get(qComm.prvcyStng));
            m.put("logoFile",   t.get(qComm.logoFile));
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> searchUsers(List<String> tokens, Long me) {
        QTblUser    qUser = QTblUser.tblUser;
        QTblComFile qPfp  = new QTblComFile("pfpFile");
        List<Tuple> rows = new JPAQueryFactory(em)
                .select(qUser.userSn, qUser.nickName, qUser.name, qUser.bio,
                        qPfp.atchFilePathNm, qPfp.strgFileNm, qPfp.atchFileExtnNm)
                .from(qUser)
                .leftJoin(qUser.pfp, qPfp)
                .where(qUser.actvtnYn.eq("Y"), qUser.status.eq("Y"), notBlocked(qUser, me), allTokens(tokens, t ->
                       qUser.nickName.containsIgnoreCase(t)
                               .or(qUser.name.containsIgnoreCase(t))
                               .or(qUser.bio.containsIgnoreCase(t))))
                .orderBy(qUser.nickName.asc())
                .limit(LIMIT_OTHER)
                .fetch();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userSn",   t.get(qUser.userSn));
            m.put("nickName", t.get(qUser.nickName));
            m.put("name",     t.get(qUser.name));
            m.put("bio",      t.get(qUser.bio));
            String pathNm = t.get(qPfp.atchFilePathNm);
            m.put("pfpUrl", pathNm != null ? pathNm + "/" + t.get(qPfp.strgFileNm) + "." + t.get(qPfp.atchFileExtnNm) : null);
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> searchTags(List<String> tokens) {
        QTblTag qTag = QTblTag.tblTag;
        return toTagMaps(new JPAQueryFactory(em)
                .select(qTag.tagSn, qTag.tagNm, qTag.useCount)
                .from(qTag)
                .where(qTag.actvtnYn.eq("Y"), anyToken(tokens, qTag.tagNm::containsIgnoreCase))
                .orderBy(qTag.useCount.desc())
                .limit(LIMIT_OTHER)
                .fetch(), qTag);
    }

    private List<Map<String, Object>> popularTags() {
        QTblTag qTag = QTblTag.tblTag;
        return toTagMaps(new JPAQueryFactory(em)
                .select(qTag.tagSn, qTag.tagNm, qTag.useCount)
                .from(qTag)
                .where(qTag.actvtnYn.eq("Y"), qTag.useCount.gt(0))
                .orderBy(qTag.useCount.desc())
                .limit(24)
                .fetch(), qTag);
    }

    private static BooleanExpression allTokens(List<String> tokens, java.util.function.Function<String, BooleanExpression> f) {
        BooleanExpression e = null;
        for (String t : tokens) e = e == null ? f.apply(t) : e.and(f.apply(t));
        return e;
    }

    private static BooleanExpression anyToken(List<String> tokens, java.util.function.Function<String, BooleanExpression> f) {
        BooleanExpression e = null;
        for (String t : tokens) e = e == null ? f.apply(t) : e.or(f.apply(t));
        return e;
    }

    /** 차단은 양방향 — 내가 막은 사람도, 나를 막은 사람도 검색에 안 나온다. */
    private static BooleanExpression notBlocked(QTblUser u, Long me) {
        if (me == null) return null;
        QTblUserBlock b = QTblUserBlock.tblUserBlock;
        return u.userSn.notIn(JPAExpressions.select(b.blockedSn).from(b).where(b.userSn.eq(me)))
                .and(u.userSn.notIn(JPAExpressions.select(b.userSn).from(b).where(b.blockedSn.eq(me))));
    }

    private static List<Map<String, Object>> toTagMaps(List<Tuple> rows, QTblTag qTag) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tagSn",    t.get(qTag.tagSn));
            m.put("tagNm",    t.get(qTag.tagNm));
            m.put("useCount", t.get(qTag.useCount));
            list.add(m);
        }
        return list;
    }
}
