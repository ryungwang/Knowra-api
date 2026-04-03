package com.knowra.community.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.common.entity.QTblComFile;
import com.knowra.community.entity.QTblComm;
import com.knowra.community.entity.QTblCommMbr;
import com.knowra.community.entity.QTblCommPost;
import com.knowra.user.entity.TblUserActionLog;
import com.knowra.user.entity.TblUserInterestScore;
import com.knowra.user.repository.TblUserInterestScoreRepository;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityExploreService {

    private final JwtProvider jwtProvider;
    private final TblUserInterestScoreRepository interestScoreRepository;

    @PersistenceContext
    private EntityManager em;

    // ── 알고리즘 가중치 (C1 관심사일치 미구현, C2~C6 사용) ────────────────
    private static final double C2 = 0.25; // 커뮤니티행동유사점수
    private static final double C3 = 0.35; // 커뮤니티관계점수
    private static final double C4 = 0.25; // 커뮤니티품질점수
    private static final double C5 = 0.10; // 커뮤니티신선도점수
    private static final double C6 = 0.05; // 커뮤니티탐험점수

    // ── 내부 DTO ──────────────────────────────────────────────────────────
    private record QualityStat(long postCnt, long viewCnt, long cmtCnt, LocalDateTime latestPostDt) {
        static final QualityStat EMPTY = new QualityStat(0, 0, 0, null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 맞춤 10 + 인기 10
    // ─────────────────────────────────────────────────────────────────────
    public ResultVO getExploreCommunities(String token) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            List<Map<String, Object>> candidates = fetchCandidatesFiltered(userSn, Map.of());
            if (candidates.isEmpty()) {
                resultVO.putResult("curated", List.of());
                resultVO.putResult("popular", List.of());
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
                return resultVO;
            }

            Map<Long, Double>      interestMap = buildInterestMap(userSn);
            Set<Long>              joinedSet   = fetchJoinedSet(userSn);
            Map<Long, QualityStat> qualityMap  = fetchQualityStats();

            List<Map<String, Object>> scored = score(candidates, interestMap, joinedSet, qualityMap);

            // 맞춤 10: 미가입 커뮤니티 우선, 개인화 총점 내림차순
            List<Map<String, Object>> curated = scored.stream()
                    .filter(c -> !(boolean) c.get("isMember"))
                    .sorted(Comparator.comparingDouble(c -> -((double) c.get("curatedScore"))))
                    .limit(10)
                    .collect(Collectors.toList());

            // 인기 10: 전체 대상, 인기 점수 내림차순
            List<Map<String, Object>> popular = scored.stream()
                    .sorted(Comparator.comparingDouble(c -> -((double) c.get("popularScore"))))
                    .limit(10)
                    .collect(Collectors.toList());

            resultVO.putResult("curated", curated);
            resultVO.putResult("popular", popular);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 일반 필터 20 (offset 기반)
    // ─────────────────────────────────────────────────────────────────────
    public ResultVO getExploreFilterCommunities(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            Long userSn = token != null ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;

            int page = params.get("pageIndex") != null
                    ? Integer.parseInt(params.get("pageIndex").toString()) : 0;
            int size = 20;

            List<Map<String, Object>> candidates = fetchCandidatesFiltered(userSn, params);
            if (candidates.isEmpty()) {
                resultVO.putResult("list",     List.of());
                resultVO.putResult("totalCnt", 0);
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
                return resultVO;
            }

            Map<Long, Double>      interestMap = userSn != null ? buildInterestMap(userSn) : Map.of();
            Set<Long>              joinedSet   = userSn != null ? fetchJoinedSet(userSn) : Set.of();
            Map<Long, QualityStat> qualityMap  = fetchQualityStats();

            List<Map<String, Object>> scored = score(candidates, interestMap, joinedSet, qualityMap);

            // 일반 점수로 내림차순 정렬
            scored.sort(Comparator.comparingDouble(c -> -((double) c.get("generalScore"))));

            int from = page * size;
            int to   = Math.min(from + size, scored.size());
            List<Map<String, Object>> list = from < scored.size() ? scored.subList(from, to) : List.of();

            resultVO.putResult("list",     list);
            resultVO.putResult("totalCnt", scored.size());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 공통 점수 계산
    // ─────────────────────────────────────────────────────────────────────
    private List<Map<String, Object>> score(
            List<Map<String, Object>> candidates,
            Map<Long, Double> interestMap,
            Set<Long> joinedSet,
            Map<Long, QualityStat> qualityMap) {

        return candidates.stream().map(c -> {
            long commSn = (long) c.get("commSn");

            double interestScore = interestMap.getOrDefault(commSn, 0.0);
            boolean isMember     = joinedSet.contains(commSn);
            QualityStat stat     = qualityMap.getOrDefault(commSn, QualityStat.EMPTY);

            // 커뮤니티품질점수
            double qualityScore = stat.postCnt() * 3.0
                    + stat.cmtCnt()  * 2.0
                    + stat.viewCnt() * 0.2;

            // 커뮤니티신선도점수
            double tdHours = stat.latestPostDt() != null
                    ? ChronoUnit.HOURS.between(stat.latestPostDt(), LocalDateTime.now()) : 720;
            double td = 1.0 / Math.pow(Math.max(0, tdHours) + 2, 0.5);
            double freshnessScore = 100 * td;

            // 커뮤니티탐험점수: 익숙하지 않을수록 높음 (JOIN 8점 기준)
            double familiarity  = Math.min(1.0, interestScore / 8.0);
            double exploreScore = (1.0 - familiarity) * 20;

            // 커뮤니티행동유사점수 (JOIN 이력 proxy)
            double behaviorScore = interestScore;

            // 커뮤니티관계점수
            double relationScore = (isMember ? 20.0 : 0.0) + interestScore * 1.5;

            // 개인화 총점
            double totalScore = behaviorScore  * C2
                    + relationScore  * C3
                    + qualityScore   * C4
                    + freshnessScore * C5
                    + exploreScore   * C6;

            Map<String, Object> result = new LinkedHashMap<>(c);
            result.put("isMember",     isMember);
            result.put("curatedScore", totalScore);
            result.put("popularScore", totalScore * 0.7 + qualityScore * 0.3);
            result.put("generalScore", totalScore * 0.5 + exploreScore * 0.5);
            return result;
        }).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 후보 커뮤니티 조회 (keyword / ctgrSn 필터 포함)
    // ─────────────────────────────────────────────────────────────────────
    private List<Map<String, Object>> fetchCandidatesFiltered(Long userSn, Map<String, Object> params) {
        QTblComm    qComm = QTblComm.tblComm;
        QTblComFile qLogo = new QTblComFile("logoFile");
        QTblCommMbr qMbr  = QTblCommMbr.tblCommMbr;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(qComm.actvtnYn.eq("Y").and(qComm.stat.eq("Y")));

        if (userSn != null) {
            var memberExists = JPAExpressions.selectOne().from(qMbr)
                    .where(qMbr.commSn.eq(qComm.commSn)
                            .and(qMbr.userSn.eq(userSn))
                            .and(qMbr.stat.eq("ACTIVE")))
                    .exists();
            builder.and(qComm.prvcyStng.in("public", "anonymous").or(memberExists));
        } else {
            builder.and(qComm.prvcyStng.in("public", "anonymous"));
        }

        if (params.get("ctgrSn") != null) {
            builder.and(qComm.ctgrSn.eq(Long.parseLong(params.get("ctgrSn").toString())));
        }

        if (params.get("keyword") != null && !params.get("keyword").toString().isBlank()) {
            String kw = params.get("keyword").toString();
            builder.and(qComm.commDsplNm.containsIgnoreCase(kw)
                    .or(qComm.commDesc.containsIgnoreCase(kw)));
        }

        List<Tuple> rows = new JPAQueryFactory(em)
                .select(qComm.commSn, qComm.commNm, qComm.commDsplNm, qComm.commDesc,
                        qComm.ctgrSn, qComm.memberCnt, qComm.prvcyStng,
                        qLogo.atchFilePathNm, qLogo.strgFileNm, qLogo.atchFileExtnNm)
                .from(qComm)
                .leftJoin(qComm.logoFile, qLogo)
                .where(builder)
                .fetch();

        return rows.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("commSn",     t.get(qComm.commSn));
            m.put("commNm",     t.get(qComm.commNm));
            m.put("commDsplNm", t.get(qComm.commDsplNm));
            m.put("commDesc",   t.get(qComm.commDesc));
            m.put("ctgrSn",     t.get(qComm.ctgrSn));
            m.put("memberCnt",  t.get(qComm.memberCnt));
            m.put("prvcyStng",  t.get(qComm.prvcyStng));
            String pathNm = t.get(qLogo.atchFilePathNm);
            m.put("logoUrl", pathNm != null
                    ? pathNm + "/" + t.get(qLogo.strgFileNm) + "." + t.get(qLogo.atchFileExtnNm)
                    : null);
            return m;
        }).toList();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 유저 커뮤니티 관심도 점수 Map<commSn, score>
    // ─────────────────────────────────────────────────────────────────────
    private Map<Long, Double> buildInterestMap(long userSn) {
        return interestScoreRepository
                .findAllByUserSnAndTargetType(userSn, TblUserActionLog.TARGET_COMM)
                .stream()
                .collect(Collectors.toMap(
                        TblUserInterestScore::getTargetSn,
                        TblUserInterestScore::getScore
                ));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 가입 커뮤니티 집합
    // ─────────────────────────────────────────────────────────────────────
    private Set<Long> fetchJoinedSet(long userSn) {
        QTblCommMbr qMbr = QTblCommMbr.tblCommMbr;
        return new HashSet<>(new JPAQueryFactory(em)
                .select(qMbr.commSn)
                .from(qMbr)
                .where(qMbr.userSn.eq(userSn).and(qMbr.stat.eq("ACTIVE")))
                .fetch());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 최근 7일 커뮤니티별 품질 통계
    // ─────────────────────────────────────────────────────────────────────
    private Map<Long, QualityStat> fetchQualityStats() {
        QTblCommPost qPost        = QTblCommPost.tblCommPost;
        LocalDateTime since       = LocalDateTime.now().minusDays(7);

        var postCntExpr  = qPost.commSn.count();
        var viewSumExpr  = qPost.viewCnt.sum();
        var cmtSumExpr   = qPost.cmtCnt.sum();
        var latestExpr   = qPost.frstCrtDt.max();

        List<Tuple> rows = new JPAQueryFactory(em)
                .select(qPost.commSn, postCntExpr, viewSumExpr, cmtSumExpr, latestExpr)
                .from(qPost)
                .where(qPost.frstCrtDt.goe(since).and(qPost.actvtnYn.eq("Y")))
                .groupBy(qPost.commSn)
                .fetch();

        Map<Long, QualityStat> map = new HashMap<>();
        for (Tuple t : rows) {
            long          commSn   = t.get(qPost.commSn);
            long          postCnt  = Optional.ofNullable(t.get(postCntExpr)).orElse(0L);
            Number        viewNum  = (Number) t.toArray()[2];
            Number        cmtNum   = (Number) t.toArray()[3];
            long          viewCnt  = viewNum != null ? viewNum.longValue() : 0L;
            long          cmtCnt   = cmtNum  != null ? cmtNum.longValue()  : 0L;
            LocalDateTime latest   = t.get(latestExpr);
            map.put(commSn, new QualityStat(postCnt, viewCnt, cmtCnt, latest));
        }
        return map;
    }
}
