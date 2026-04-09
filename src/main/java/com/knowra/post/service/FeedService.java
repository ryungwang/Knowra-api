package com.knowra.post.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.common.CommunityQueryHelper;
import com.knowra.common.entity.QTblComFile;
import com.knowra.common.service.TagService;
import com.knowra.community.entity.*;
import com.knowra.post.entity.*;
import com.knowra.user.entity.*;
import com.knowra.user.repository.TblUserInterestScoreRepository;
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
public class FeedService {

    @PersistenceContext
    private EntityManager em;

    private final JwtProvider jwtProvider;
    private final TblUserInterestScoreRepository interestScoreRepository;
    private final TagService tagService;
    private final FeedCacheService feedCacheService;

    private static final int PAGE_SIZE      = 50;
    private static final int MAX_CANDIDATES = 500;

    // ── 알고리즘 가중치 ────────────────────────────────────────────────────
    private static final double W1 = 0.15; // 관심사일치
    private static final double W3 = 0.25; // 관계
    private static final double W4 = 0.25; // 품질
    private static final double W5 = 0.20; // 신선도
    private static final double W6 = 0.15; // 탐험

    // ── 내부 DTO ──────────────────────────────────────────────────────────
    private record FeedPost(String postKind, String postTyp, long postSn, long userSn,
                             String nickName, String authorNm,
                             String postTtl, String postCntnt, LocalDateTime frstCrtDt,
                             int viewCnt, int likeCnt, int cmtCnt,
                             Long commSn, String commNm, String commDsplNm, String pfpUrl) {}

    // ─────────────────────────────────────────────────────────────────────
    // 홈 피드 (전체 개인화)
    // ─────────────────────────────────────────────────────────────────────
    public ResultVO getPersonalizedFeed(Map<String, Object> params, String token) {
        return buildFeed(params, token, "PERSONALIZED");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 팔로잉 피드
    // ─────────────────────────────────────────────────────────────────────
    public ResultVO getFollowingFeed(Map<String, Object> params, String token) {
        return buildFeed(params, token, "FOLLOWING");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 인기 피드
    // ─────────────────────────────────────────────────────────────────────
    public ResultVO getPopularFeed(Map<String, Object> params, String token) {
        return buildFeed(params, token, "POPULAR");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 최신 피드
    // ─────────────────────────────────────────────────────────────────────
    public ResultVO getLatestFeed(Map<String, Object> params, String token) {
        return buildFeed(params, token, "LATEST");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 공통 피드 빌더
    // ─────────────────────────────────────────────────────────────────────
    private ResultVO buildFeed(Map<String, Object> params, String token, String mode) {
        ResultVO resultVO = new ResultVO();
        try {
            Long userSn = token != null
                    ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;
            int page = params.get("pageIndex") != null
                    ? Integer.parseInt(params.get("pageIndex").toString()) : 0;

            String cacheKey = buildCacheKey(mode, userSn);

            // ── Cache HIT ────────────────────────────────────────────────
            if (cacheKey != null && feedCacheService.exists(cacheKey)) {
                List<String>   members  = feedCacheService.getPage(cacheKey, page, PAGE_SIZE);
                long           total    = feedCacheService.size(cacheKey);
                List<FeedPost> items    = hydrateMembers(members, userSn);
                List<PostDTO>  list     = buildPostDtoList(items, userSn);

                resultVO.putResult("list",     list);
                resultVO.putResult("totalCnt", total);
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
                return resultVO;
            }

            // ── Cache MISS: DB 후보 수집 ─────────────────────────────────
            Set<Long> followedUserSns = userSn != null ? fetchFollowedUserSns(userSn) : Set.of();
            Set<Long> joinedCommSns   = userSn != null ? fetchJoinedCommSns(userSn)   : Set.of();
            List<FeedPost> candidates = collectCandidates(userSn, followedUserSns, joinedCommSns, mode);

            if (candidates.isEmpty()) {
                resultVO.putResult("list",     List.of());
                resultVO.putResult("totalCnt", 0);
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
                return resultVO;
            }

            // 점수 계산 데이터 준비
            Map<Long, Double> userTagScores = userSn != null ? buildTagScoreMap(userSn)    : Map.of();
            Map<Long, Double> authorScores  = userSn != null ? buildAuthorScoreMap(userSn) : Map.of();
            Map<Long, Double> commScores    = userSn != null ? buildCommScoreMap(userSn)   : Map.of();

            List<Long> allPostSns     = candidates.stream().filter(p -> "POST".equals(p.postKind())).map(FeedPost::postSn).toList();
            List<Long> allCommPostSns = candidates.stream().filter(p -> "COMM".equals(p.postKind())).map(FeedPost::postSn).toList();
            Map<Long, List<Long>> postTagSnMap     = fetchPostTagSnMap(allPostSns);
            Map<Long, List<Long>> commPostTagSnMap = fetchCommPostTagSnMap(allCommPostSns);

            // 점수를 Map에 미리 계산 (정렬 + Redis 저장 공용)
            Map<String, Double> memberScores = new LinkedHashMap<>();
            candidates.forEach(p -> memberScores.put(
                    p.postKind() + ":" + p.postSn(),
                    computeScore(p, mode, userTagScores, authorScores, commScores,
                            followedUserSns, joinedCommSns, postTagSnMap, commPostTagSnMap)));

            List<FeedPost> scored = candidates.stream()
                    .sorted(Comparator.comparingDouble(p -> -memberScores.get(p.postKind() + ":" + p.postSn())))
                    .collect(Collectors.toList());

            // Redis 캐시 저장
            if (cacheKey != null) {
                feedCacheService.populate(cacheKey, memberScores);
                if ("PERSONALIZED".equals(mode)) {
                    feedCacheService.expirePersonalized(cacheKey);
                }
            }

            // 페이지네이션
            int from      = page * PAGE_SIZE;
            int to        = Math.min(from + PAGE_SIZE, scored.size());
            List<FeedPost> pageItems = from < scored.size() ? scored.subList(from, to) : List.of();

            resultVO.putResult("list",     buildPostDtoList(pageItems, userSn));
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
    // 캐시 키 생성 (null = 캐시 미적용)
    // ─────────────────────────────────────────────────────────────────────
    private String buildCacheKey(String mode, Long userSn) {
        return switch (mode) {
            case "FOLLOWING"    -> userSn != null ? feedCacheService.followingKey(userSn)    : null;
            case "PERSONALIZED" -> userSn != null ? feedCacheService.personalizedKey(userSn) : null;
            case "POPULAR"      -> feedCacheService.popularKey();
            case "LATEST"       -> feedCacheService.latestKey();
            default             -> null;
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // 캐시 멤버 → FeedPost (ID만 저장된 캐시에서 DB 배치 조회)
    // ─────────────────────────────────────────────────────────────────────
    private List<FeedPost> hydrateMembers(List<String> members, Long viewerSn) {
        List<Long> postSns     = new ArrayList<>();
        List<Long> commPostSns = new ArrayList<>();
        for (String m : members) {
            String[] parts = m.split(":", 2);
            if (parts.length < 2) continue;
            if ("POST".equals(parts[0])) postSns.add(Long.parseLong(parts[1]));
            else                          commPostSns.add(Long.parseLong(parts[1]));
        }

        Map<Long, FeedPost> postMap     = fetchPostsByIds(postSns).stream()
                .collect(Collectors.toMap(FeedPost::postSn, p -> p));
        Map<Long, FeedPost> commPostMap = fetchCommPostsByIds(commPostSns, viewerSn).stream()
                .collect(Collectors.toMap(FeedPost::postSn, p -> p));

        // Redis 순서(score DESC) 유지
        return members.stream()
                .map(m -> {
                    String[] parts = m.split(":", 2);
                    if (parts.length < 2) return null;
                    long sn = Long.parseLong(parts[1]);
                    return "POST".equals(parts[0]) ? postMap.get(sn) : commPostMap.get(sn);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // FeedPost 리스트 → PostDTO 리스트 (tagNms / 좋아요 / 저장 배치 조회)
    // ─────────────────────────────────────────────────────────────────────
    private List<PostDTO> buildPostDtoList(List<FeedPost> items, Long userSn) {
        List<Long> pagePostSns     = items.stream().filter(p -> "POST".equals(p.postKind())).map(FeedPost::postSn).toList();
        List<Long> pageCommPostSns = items.stream().filter(p -> "COMM".equals(p.postKind())).map(FeedPost::postSn).toList();

        Map<Long, List<String>> postTagNms     = tagService.fetchTagMap(pagePostSns,     "post");
        Map<Long, List<String>> commPostTagNms = tagService.fetchTagMap(pageCommPostSns, "commPost");

        Map<Long, String> myPostLikeMap     = userSn != null ? fetchMyPostLikeMap(userSn, pagePostSns)         : Map.of();
        Map<Long, String> myCommPostLikeMap = userSn != null ? fetchMyCommPostLikeMap(userSn, pageCommPostSns) : Map.of();
        Set<Long>         mySavedPostSet    = userSn != null ? fetchMySavedSet(userSn, pagePostSns,     "POST") : Set.of();
        Set<Long>         mySavedCommSet    = userSn != null ? fetchMySavedSet(userSn, pageCommPostSns, "COMM") : Set.of();

        return items.stream().map(p -> {
            PostDTO dto;
            if ("COMM".equals(p.postKind())) {
                dto = new PostDTO("COMM", p.postTyp(),
                        Optional.ofNullable(p.commSn()).orElse(0L),
                        p.commNm(), p.commDsplNm(),
                        p.postSn(), p.userSn(), p.nickName(), p.authorNm(),
                        p.postTtl(), p.postCntnt(), p.frstCrtDt(),
                        p.viewCnt(), p.likeCnt(), p.cmtCnt(),
                        myCommPostLikeMap.get(p.postSn()), mySavedCommSet.contains(p.postSn()));
                dto.setTagNms(commPostTagNms.getOrDefault(p.postSn(), List.of()));
            } else {
                dto = new PostDTO("POST", p.postTyp(),
                        p.postSn(), p.userSn(), p.nickName(), p.authorNm(),
                        p.postTtl(), p.postCntnt(), p.frstCrtDt(),
                        p.viewCnt(), p.likeCnt(), p.cmtCnt(),
                        myPostLikeMap.get(p.postSn()), mySavedPostSet.contains(p.postSn()));
                dto.setTagNms(postTagNms.getOrDefault(p.postSn(), List.of()));
            }
            dto.setPfpUrl(p.pfpUrl());
            return dto;
        }).toList();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 점수 계산
    // ─────────────────────────────────────────────────────────────────────
    private double computeScore(FeedPost p, String mode,
                                 Map<Long, Double> userTagScores,
                                 Map<Long, Double> authorScores,
                                 Map<Long, Double> commScores,
                                 Set<Long> followedUserSns,
                                 Set<Long> joinedCommSns,
                                 Map<Long, List<Long>> postTagSnMap,
                                 Map<Long, List<Long>> commPostTagSnMap) {

        // 관심사일치점수 (W1): Σ(유저태그점수 × 게시글태그 포함 여부)
        List<Long> tagSns = "COMM".equals(p.postKind())
                ? commPostTagSnMap.getOrDefault(p.postSn(), List.of())
                : postTagSnMap.getOrDefault(p.postSn(), List.of());
        double interestMatchScore = tagSns.stream()
                .mapToDouble(t -> userTagScores.getOrDefault(t, 0.0))
                .sum();

        // 품질점수 (W4)
        double qualityScore = p.likeCnt() * 2.0 + p.cmtCnt() * 4.0 + p.viewCnt() * 0.2;

        // 신선도점수 (W5): TD = 1 / (경과시간 + 2)^0.5
        long   elapsedHours   = ChronoUnit.HOURS.between(p.frstCrtDt(), LocalDateTime.now());
        double td             = 1.0 / Math.pow(Math.max(0, elapsedHours) + 2, 0.5);
        double freshnessScore = 100 * td;

        // 관계점수 (W3)
        boolean isFollowedAuthor = followedUserSns.contains(p.userSn());
        boolean isJoinedComm     = p.commSn() != null && joinedCommSns.contains(p.commSn());
        double  authorInterest   = authorScores.getOrDefault(p.userSn(), 0.0);
        double  commInterest     = p.commSn() != null ? commScores.getOrDefault(p.commSn(), 0.0) : 0.0;
        double  relationScore    = (isFollowedAuthor ? 15.0 : 0.0)
                + (isJoinedComm ? 20.0 : 0.0)
                + authorInterest * 1.0
                + commInterest   * 1.2;

        // 탐험점수 (W6): 익숙하지 않은 게시글일수록 높음
        double familiarity  = Math.min(1.0, (authorInterest + commInterest) / 20.0);
        double novelty      = tagSns.isEmpty() ? 1.0
                : tagSns.stream()
                .mapToDouble(t -> userTagScores.getOrDefault(t, 0.0) == 0.0 ? 1.0 : 0.0)
                .average().orElse(0.0);
        double exploreScore = (1.0 - familiarity) * novelty * 20;

        double totalScore = interestMatchScore * W1
                + relationScore   * W3
                + qualityScore    * W4
                + freshnessScore  * W5
                + exploreScore    * W6;

        return switch (mode) {
            case "FOLLOWING" -> totalScore * 0.7 + (isFollowedAuthor ? 15.0 : 0.0) + (isJoinedComm ? 20.0 : 0.0);
            case "POPULAR"   -> totalScore * 0.6 + qualityScore   * 0.4;
            case "LATEST"    -> totalScore * 0.7 + freshnessScore * 0.3;
            default          -> totalScore; // PERSONALIZED
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // 후보 수집
    // ─────────────────────────────────────────────────────────────────────
    private List<FeedPost> collectCandidates(Long userSn, Set<Long> followedUserSns,
                                               Set<Long> joinedCommSns, String mode) {
        Set<String>    dedup  = new LinkedHashSet<>();
        List<FeedPost> result = new ArrayList<>();

        if ("FOLLOWING".equals(mode)) {
            // 팔로잉 피드: 팔로우한 유저가 작성한 게시글만 (일반 + 커뮤니티 모두)
            if (!followedUserSns.isEmpty()) {
                fetchPostsByUsers(followedUserSns).stream()
                        .filter(p -> dedup.add("POST:" + p.postSn()))
                        .forEach(result::add);
                fetchCommPostsByUsers(followedUserSns, userSn).stream()
                        .filter(p -> dedup.add("COMM:" + p.postSn()))
                        .forEach(result::add);
            }
            return result.stream().limit(MAX_CANDIDATES).collect(Collectors.toList());
        }

        if ("POPULAR".equals(mode)) {
            // 인기 피드: 반응 수 기준 상위 게시글 (날짜 무관)
            fetchTopPostsByQuality(MAX_CANDIDATES / 2).stream()
                    .filter(p -> dedup.add("POST:" + p.postSn()))
                    .forEach(result::add);
            fetchTopCommPostsByQuality(MAX_CANDIDATES / 2, userSn).stream()
                    .filter(p -> dedup.add("COMM:" + p.postSn()))
                    .forEach(result::add);
            return result.stream().limit(MAX_CANDIDATES).collect(Collectors.toList());
        }

        if ("LATEST".equals(mode)) {
            // 최신 피드: 전체 최신 게시글 500개
            fetchLatestPosts(MAX_CANDIDATES / 2).stream()
                    .filter(p -> dedup.add("POST:" + p.postSn()))
                    .forEach(result::add);
            fetchLatestCommPosts(MAX_CANDIDATES / 2, userSn).stream()
                    .filter(p -> dedup.add("COMM:" + p.postSn()))
                    .forEach(result::add);
            return result.stream().limit(MAX_CANDIDATES).collect(Collectors.toList());
        }

        // PERSONALIZED: 팔로우한 유저의 일반 게시글 (날짜 제한 없음)
        if (!followedUserSns.isEmpty()) {
            fetchPostsByUsers(followedUserSns).stream()
                    .filter(p -> dedup.add("POST:" + p.postSn()))
                    .forEach(result::add);
        }

        // 가입한 커뮤니티 게시글 (날짜 제한 없음)
        if (!joinedCommSns.isEmpty()) {
            fetchCommPostsByComms(joinedCommSns).stream()
                    .filter(p -> dedup.add("COMM:" + p.postSn()))
                    .forEach(result::add);
        }

        // 관심 태그 기반 게시글 (90일 이내)
        if (userSn != null) {
            List<Long> interestedTagSns = fetchInterestedTagSns(userSn);
            if (!interestedTagSns.isEmpty()) {
                LocalDateTime since90 = LocalDateTime.now().minusDays(90);
                fetchPostsByTags(interestedTagSns, since90).stream()
                        .filter(p -> dedup.add("POST:" + p.postSn()))
                        .forEach(result::add);
                fetchCommPostsByTags(interestedTagSns, since90, userSn).stream()
                        .filter(p -> dedup.add("COMM:" + p.postSn()))
                        .forEach(result::add);
            }
        }

        // fallback: 비회원이거나 후보 50개 미만이면 최신 게시글로 보충
        if (userSn == null || result.size() < 50) {
            fetchLatestPosts(100).stream()
                    .filter(p -> dedup.add("POST:" + p.postSn()))
                    .forEach(result::add);
            fetchLatestCommPosts(100, userSn).stream()
                    .filter(p -> dedup.add("COMM:" + p.postSn()))
                    .forEach(result::add);
        }

        return result.stream().limit(MAX_CANDIDATES).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 팔로우/가입 집합 조회
    // ─────────────────────────────────────────────────────────────────────
    private Set<Long> fetchFollowedUserSns(long userSn) {
        QTblUserFlwr qFlwr = QTblUserFlwr.tblUserFlwr;
        return new HashSet<>(new JPAQueryFactory(em)
                .select(qFlwr.flwngUserSn)
                .from(qFlwr)
                .where(qFlwr.flwrUserSn.eq(userSn).and(qFlwr.actvtnYn.eq("Y")))
                .fetch());
    }

    private Set<Long> fetchJoinedCommSns(long userSn) {
        QTblCommMbr qMbr = QTblCommMbr.tblCommMbr;
        return new HashSet<>(new JPAQueryFactory(em)
                .select(qMbr.commSn)
                .from(qMbr)
                .where(qMbr.userSn.eq(userSn).and(qMbr.stat.eq("ACTIVE")))
                .fetch());
    }

    private List<Long> fetchInterestedTagSns(long userSn) {
        return interestScoreRepository
                .findAllByUserSnAndTargetType(userSn, TblUserActionLog.TARGET_TAG)
                .stream()
                .filter(s -> s.getScore() > 0)
                .map(TblUserInterestScore::getTargetSn)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 후보 게시글 조회
    // ─────────────────────────────────────────────────────────────────────
    private List<FeedPost> fetchPostsByUsers(Set<Long> userSns) {
        QTblPost    qPost   = QTblPost.tblPost;
        QTblUser    qUser   = QTblUser.tblUser;
        QTblComFile pfpFile = new QTblComFile("pfpFile");
        return new JPAQueryFactory(em)
                .select(qPost, qUser.nickName, qUser.name,
                        pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                .from(qPost)
                .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                .leftJoin(qUser.pfp, pfpFile)
                .where(qPost.userSn.in(userSns)
                        .and(qPost.stat.eq("ACTIVE"))
                        .and(qPost.actvtnYn.eq("Y")))
                .orderBy(qPost.frstCrtDt.desc())
                .fetch()
                .stream()
                .map(t -> toFeedPost(t, qPost, qUser, pfpFile))
                .collect(Collectors.toList());
    }

    private List<FeedPost> fetchCommPostsByUsers(Set<Long> userSns, Long viewerSn) {
        QTblCommPost qPost   = QTblCommPost.tblCommPost;
        QTblComm     qComm   = QTblComm.tblComm;
        QTblUser     qUser   = QTblUser.tblUser;
        QTblComFile  pfpFile = new QTblComFile("pfpFile");
        return new JPAQueryFactory(em)
                .select(qPost, qComm.commNm, qComm.commDsplNm,
                        qUser.nickName, qUser.name,
                        pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                .from(qPost)
                .join(qComm).on(qPost.commSn.eq(qComm.commSn))
                .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                .leftJoin(qUser.pfp, pfpFile)
                .where(qPost.userSn.in(userSns)
                        .and(qPost.stat.eq("ACTIVE"))
                        .and(qPost.actvtnYn.eq("Y"))
                        .and(CommunityQueryHelper.accessCondition(qComm, viewerSn)))
                .orderBy(qPost.frstCrtDt.desc())
                .fetch()
                .stream()
                .map(t -> toFeedCommPost(t, qPost, qComm, qUser, pfpFile))
                .collect(Collectors.toList());
    }

    private List<FeedPost> fetchCommPostsByComms(Set<Long> commSns) {
        QTblCommPost qPost   = QTblCommPost.tblCommPost;
        QTblComm     qComm   = QTblComm.tblComm;
        QTblUser     qUser   = QTblUser.tblUser;
        QTblComFile  pfpFile = new QTblComFile("pfpFile");
        return new JPAQueryFactory(em)
                .select(qPost, qComm.commNm, qComm.commDsplNm,
                        qUser.nickName, qUser.name,
                        pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                .from(qPost)
                .join(qComm).on(qPost.commSn.eq(qComm.commSn))
                .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                .leftJoin(qUser.pfp, pfpFile)
                .where(qPost.commSn.in(commSns)
                        .and(qPost.stat.eq("ACTIVE"))
                        .and(qPost.actvtnYn.eq("Y")))
                .orderBy(qPost.frstCrtDt.desc())
                .fetch()
                .stream()
                .map(t -> toFeedCommPost(t, qPost, qComm, qUser, pfpFile))
                .collect(Collectors.toList());
    }

    private List<FeedPost> fetchPostsByTags(List<Long> tagSns, LocalDateTime since) {
        QTblPost    qPost   = QTblPost.tblPost;
        QTblPostTag qTag    = QTblPostTag.tblPostTag;
        QTblUser    qUser   = QTblUser.tblUser;
        QTblComFile pfpFile = new QTblComFile("pfpFile");
        return new JPAQueryFactory(em)
                .select(qPost, qUser.nickName, qUser.name,
                        pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                .from(qPost)
                .join(qTag).on(qTag.postSn.eq(qPost.postSn).and(qTag.tagSn.in(tagSns)))
                .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                .leftJoin(qUser.pfp, pfpFile)
                .where(qPost.stat.eq("ACTIVE")
                        .and(qPost.actvtnYn.eq("Y"))
                        .and(qPost.frstCrtDt.goe(since)))
                .distinct()
                .orderBy(qPost.frstCrtDt.desc())
                .fetch()
                .stream()
                .map(t -> toFeedPost(t, qPost, qUser, pfpFile))
                .collect(Collectors.toList());
    }

    private List<FeedPost> fetchCommPostsByTags(List<Long> tagSns, LocalDateTime since, Long viewerSn) {
        QTblCommPost    qPost   = QTblCommPost.tblCommPost;
        QTblCommPostTag qTag    = QTblCommPostTag.tblCommPostTag;
        QTblComm        qComm   = QTblComm.tblComm;
        QTblUser        qUser   = QTblUser.tblUser;
        QTblComFile     pfpFile = new QTblComFile("pfpFile");
        return new JPAQueryFactory(em)
                .select(qPost, qComm.commNm, qComm.commDsplNm,
                        qUser.nickName, qUser.name,
                        pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                .from(qPost)
                .join(qTag).on(qTag.commPostSn.eq(qPost.commPostSn).and(qTag.tagSn.in(tagSns)))
                .join(qComm).on(qPost.commSn.eq(qComm.commSn))
                .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                .leftJoin(qUser.pfp, pfpFile)
                .where(qPost.stat.eq("ACTIVE")
                        .and(qPost.actvtnYn.eq("Y"))
                        .and(qPost.frstCrtDt.goe(since))
                        .and(CommunityQueryHelper.accessCondition(qComm, viewerSn)))
                .distinct()
                .orderBy(qPost.frstCrtDt.desc())
                .fetch()
                .stream()
                .map(t -> toFeedCommPost(t, qPost, qComm, qUser, pfpFile))
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // ID 배치 조회 (캐시 히트 시 사용)
    // ─────────────────────────────────────────────────────────────────────
    private List<FeedPost> fetchPostsByIds(List<Long> postSns) {
        if (postSns.isEmpty()) return List.of();
        QTblPost    qPost   = QTblPost.tblPost;
        QTblUser    qUser   = QTblUser.tblUser;
        QTblComFile pfpFile = new QTblComFile("pfpFile");
        return new JPAQueryFactory(em)
                .select(qPost, qUser.nickName, qUser.name,
                        pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                .from(qPost)
                .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                .leftJoin(qUser.pfp, pfpFile)
                .where(qPost.postSn.in(postSns)
                        .and(qPost.stat.eq("ACTIVE"))
                        .and(qPost.actvtnYn.eq("Y")))
                .fetch()
                .stream()
                .map(t -> toFeedPost(t, qPost, qUser, pfpFile))
                .collect(Collectors.toList());
    }

    private List<FeedPost> fetchCommPostsByIds(List<Long> commPostSns, Long viewerSn) {
        if (commPostSns.isEmpty()) return List.of();
        QTblCommPost qPost   = QTblCommPost.tblCommPost;
        QTblComm     qComm   = QTblComm.tblComm;
        QTblUser     qUser   = QTblUser.tblUser;
        QTblComFile  pfpFile = new QTblComFile("pfpFile");
        return new JPAQueryFactory(em)
                .select(qPost, qComm.commNm, qComm.commDsplNm,
                        qUser.nickName, qUser.name,
                        pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                .from(qPost)
                .join(qComm).on(qPost.commSn.eq(qComm.commSn))
                .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                .leftJoin(qUser.pfp, pfpFile)
                .where(qPost.commPostSn.in(commPostSns)
                        .and(qPost.stat.eq("ACTIVE"))
                        .and(qPost.actvtnYn.eq("Y"))
                        .and(CommunityQueryHelper.accessCondition(qComm, viewerSn)))
                .fetch()
                .stream()
                .map(t -> toFeedCommPost(t, qPost, qComm, qUser, pfpFile))
                .collect(Collectors.toList());
    }

    // likeCnt*2 + cmtCnt*4 + viewCnt*0.2 순 (DB 정렬은 정수 근사)
    private List<FeedPost> fetchTopPostsByQuality(int limit) {
        QTblPost    qPost   = QTblPost.tblPost;
        QTblUser    qUser   = QTblUser.tblUser;
        QTblComFile pfpFile = new QTblComFile("pfpFile");
        return new JPAQueryFactory(em)
                .select(qPost, qUser.nickName, qUser.name,
                        pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                .from(qPost)
                .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                .leftJoin(qUser.pfp, pfpFile)
                .where(qPost.stat.eq("ACTIVE").and(qPost.actvtnYn.eq("Y")))
                .orderBy(qPost.likeCnt.multiply(2).add(qPost.cmtCnt.multiply(4)).desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(t -> toFeedPost(t, qPost, qUser, pfpFile))
                .collect(Collectors.toList());
    }

    private List<FeedPost> fetchTopCommPostsByQuality(int limit, Long viewerSn) {
        QTblCommPost qPost   = QTblCommPost.tblCommPost;
        QTblComm     qComm   = QTblComm.tblComm;
        QTblUser     qUser   = QTblUser.tblUser;
        QTblComFile  pfpFile = new QTblComFile("pfpFile");
        return new JPAQueryFactory(em)
                .select(qPost, qComm.commNm, qComm.commDsplNm,
                        qUser.nickName, qUser.name,
                        pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                .from(qPost)
                .join(qComm).on(qPost.commSn.eq(qComm.commSn))
                .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                .leftJoin(qUser.pfp, pfpFile)
                .where(qPost.stat.eq("ACTIVE")
                        .and(qPost.actvtnYn.eq("Y"))
                        .and(CommunityQueryHelper.accessCondition(qComm, viewerSn)))
                .orderBy(qPost.likeCnt.multiply(2).add(qPost.cmtCnt.multiply(4)).desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(t -> toFeedCommPost(t, qPost, qComm, qUser, pfpFile))
                .collect(Collectors.toList());
    }

    private List<FeedPost> fetchLatestPosts(int limit) {
        QTblPost    qPost   = QTblPost.tblPost;
        QTblUser    qUser   = QTblUser.tblUser;
        QTblComFile pfpFile = new QTblComFile("pfpFile");
        return new JPAQueryFactory(em)
                .select(qPost, qUser.nickName, qUser.name,
                        pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                .from(qPost)
                .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                .leftJoin(qUser.pfp, pfpFile)
                .where(qPost.stat.eq("ACTIVE").and(qPost.actvtnYn.eq("Y")))
                .orderBy(qPost.frstCrtDt.desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(t -> toFeedPost(t, qPost, qUser, pfpFile))
                .collect(Collectors.toList());
    }

    private List<FeedPost> fetchLatestCommPosts(int limit, Long viewerSn) {
        QTblCommPost qPost   = QTblCommPost.tblCommPost;
        QTblComm     qComm   = QTblComm.tblComm;
        QTblUser     qUser   = QTblUser.tblUser;
        QTblComFile  pfpFile = new QTblComFile("pfpFile");
        return new JPAQueryFactory(em)
                .select(qPost, qComm.commNm, qComm.commDsplNm,
                        qUser.nickName, qUser.name,
                        pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                .from(qPost)
                .join(qComm).on(qPost.commSn.eq(qComm.commSn))
                .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                .leftJoin(qUser.pfp, pfpFile)
                .where(qPost.stat.eq("ACTIVE").and(qPost.actvtnYn.eq("Y"))
                        .and(CommunityQueryHelper.accessCondition(qComm, viewerSn)))
                .orderBy(qPost.frstCrtDt.desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(t -> toFeedCommPost(t, qPost, qComm, qUser, pfpFile))
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 태그 SN 배치 조회 (점수 계산용)
    // ─────────────────────────────────────────────────────────────────────
    private Map<Long, List<Long>> fetchPostTagSnMap(List<Long> postSns) {
        if (postSns.isEmpty()) return Map.of();
        QTblPostTag qTag = QTblPostTag.tblPostTag;
        Map<Long, List<Long>> result = new HashMap<>();
        new JPAQueryFactory(em)
                .select(qTag.postSn, qTag.tagSn)
                .from(qTag)
                .where(qTag.postSn.in(postSns))
                .fetch()
                .forEach(t -> result.computeIfAbsent(t.get(qTag.postSn), k -> new ArrayList<>())
                        .add(t.get(qTag.tagSn)));
        return result;
    }

    private Map<Long, List<Long>> fetchCommPostTagSnMap(List<Long> commPostSns) {
        if (commPostSns.isEmpty()) return Map.of();
        QTblCommPostTag qTag = QTblCommPostTag.tblCommPostTag;
        Map<Long, List<Long>> result = new HashMap<>();
        new JPAQueryFactory(em)
                .select(qTag.commPostSn, qTag.tagSn)
                .from(qTag)
                .where(qTag.commPostSn.in(commPostSns))
                .fetch()
                .forEach(t -> result.computeIfAbsent(t.get(qTag.commPostSn), k -> new ArrayList<>())
                        .add(t.get(qTag.tagSn)));
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 내 좋아요 배치 조회
    // ─────────────────────────────────────────────────────────────────────
    private Map<Long, String> fetchMyPostLikeMap(long userSn, List<Long> postSns) {
        if (postSns.isEmpty()) return Map.of();
        QTblPostLike qLike = QTblPostLike.tblPostLike;
        Map<Long, String> result = new HashMap<>();
        new JPAQueryFactory(em)
                .select(qLike.postSn, qLike.likeTyp)
                .from(qLike)
                .where(qLike.userSn.eq(userSn).and(qLike.postSn.in(postSns)))
                .fetch()
                .forEach(t -> result.put(t.get(qLike.postSn), t.get(qLike.likeTyp)));
        return result;
    }

    private Map<Long, String> fetchMyCommPostLikeMap(long userSn, List<Long> commPostSns) {
        if (commPostSns.isEmpty()) return Map.of();
        QTblCommPostLike qLike = QTblCommPostLike.tblCommPostLike;
        Map<Long, String> result = new HashMap<>();
        new JPAQueryFactory(em)
                .select(qLike.commPostSn, qLike.likeTyp)
                .from(qLike)
                .where(qLike.userSn.eq(userSn).and(qLike.commPostSn.in(commPostSns)))
                .fetch()
                .forEach(t -> result.put(t.get(qLike.commPostSn), t.get(qLike.likeTyp)));
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 내 저장 여부 배치 조회
    // ─────────────────────────────────────────────────────────────────────
    private Set<Long> fetchMySavedSet(long userSn, List<Long> postSns, String postKind) {
        if (postSns.isEmpty()) return Set.of();
        QTblPostSave qSave = QTblPostSave.tblPostSave;
        return new HashSet<>(new JPAQueryFactory(em)
                .select(qSave.postSn)
                .from(qSave)
                .where(qSave.userSn.eq(userSn)
                        .and(qSave.postKind.eq(postKind))
                        .and(qSave.postSn.in(postSns))
                        .and(qSave.actvtnYn.eq("Y")))
                .fetch());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 관심도 점수 Map 빌더
    // ─────────────────────────────────────────────────────────────────────
    private Map<Long, Double> buildTagScoreMap(long userSn) {
        return interestScoreRepository.findAllByUserSnAndTargetType(userSn, TblUserActionLog.TARGET_TAG)
                .stream().collect(Collectors.toMap(TblUserInterestScore::getTargetSn, TblUserInterestScore::getScore));
    }

    private Map<Long, Double> buildAuthorScoreMap(long userSn) {
        return interestScoreRepository.findAllByUserSnAndTargetType(userSn, TblUserActionLog.TARGET_USER)
                .stream().collect(Collectors.toMap(TblUserInterestScore::getTargetSn, TblUserInterestScore::getScore));
    }

    private Map<Long, Double> buildCommScoreMap(long userSn) {
        return interestScoreRepository.findAllByUserSnAndTargetType(userSn, TblUserActionLog.TARGET_COMM)
                .stream().collect(Collectors.toMap(TblUserInterestScore::getTargetSn, TblUserInterestScore::getScore));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tuple → FeedPost 변환
    // ─────────────────────────────────────────────────────────────────────
    private FeedPost toFeedPost(com.querydsl.core.Tuple t,
                                 QTblPost qPost, QTblUser qUser, QTblComFile pfpFile) {
        TblPost p     = t.get(qPost);
        String pathNm = t.get(pfpFile.atchFilePathNm);
        String pfpUrl = pathNm != null
                ? pathNm + "/" + t.get(pfpFile.strgFileNm) + "." + t.get(pfpFile.atchFileExtnNm) : null;
        return new FeedPost("POST", p.getPostTyp(),
                Optional.ofNullable(p.getPostSn()).orElse(0L), p.getUserSn(),
                t.get(qUser.nickName), t.get(qUser.name),
                p.getPostTtl(), p.getPostCntnt(), p.getFrstCrtDt(),
                p.getViewCnt(), p.getLikeCnt(), p.getCmtCnt(),
                null, null, null, pfpUrl);
    }

    private FeedPost toFeedCommPost(com.querydsl.core.Tuple t,
                                     QTblCommPost qPost, QTblComm qComm,
                                     QTblUser qUser, QTblComFile pfpFile) {
        TblCommPost p = t.get(qPost);
        String pathNm = t.get(pfpFile.atchFilePathNm);
        String pfpUrl = pathNm != null
                ? pathNm + "/" + t.get(pfpFile.strgFileNm) + "." + t.get(pfpFile.atchFileExtnNm) : null;
        return new FeedPost("COMM", p.getPostTyp(),
                Optional.ofNullable(p.getCommPostSn()).orElse(0L), p.getUserSn(),
                t.get(qUser.nickName), t.get(qUser.name),
                p.getPostTtl(), p.getPostCntnt(), p.getFrstCrtDt(),
                p.getViewCnt(), p.getLikeCnt(), p.getCmtCnt(),
                p.getCommSn(), t.get(qComm.commNm), t.get(qComm.commDsplNm), pfpUrl);
    }
}
