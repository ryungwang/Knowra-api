package com.knowra.post.service;

import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.community.entity.*;
import com.knowra.post.config.FeedRankingProperties;
import com.knowra.post.entity.*;
import com.knowra.user.entity.*;
import com.knowra.common.entity.TblTag;
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

import static com.knowra.post.service.PostQuery.POST;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

    @PersistenceContext
    private EntityManager em;

    private final JwtProvider jwtProvider;
    private final TblUserInterestScoreRepository interestScoreRepository;
    private final com.knowra.common.repository.TblTagRepository tblTagRepository;
    private final PostQuery postQuery;
    private final FeedCacheService feedCacheService;
    private final FeedRankingProperties ranking;

    private static final int PAGE_SIZE      = 50;
    private static final int MAX_CANDIDATES = 500;

    // ── 알고리즘 가중치·기준값 ────────────────────────────────────────────
    // 값은 FeedRankingProperties(knowra.feed.ranking.*)가 들고 있다. 랭킹 상수는 반드시
    // 여러 번 고치게 되므로 재배포 없이 조정할 수 있어야 한다.
    //
    // 다섯 축을 모두 [0,1] 로 정규화한 뒤 곱하므로, 가중치는 그 축이 가져갈 수 있는
    // **상한 지분** 이다. 합이 1.0 이라 총점도 [0,1] 에 들어온다 (기동 시 검증한다).
    //
    // 정규화 전에는 그렇지 않았다. 원점수 범위가 관심사 0~12, 품질 0~3,598 로
    // 제각각이라 0.25 를 곱해도 지분이 되지 않았고, 실측 결과 품질 축이 순위 변동의
    // 97.8% 를 차지해 개인화 순서와 순수 인기순의 스피어만 상관이 0.9992 였다.
    // 근거와 측정 절차: docs/feed-ranking.md

    // ── 내부 DTO ──────────────────────────────────────────────────────────
    // package-private: 같은 패키지의 랭킹 불변식 테스트(FeedRankingTest)가 후보를
    // 직접 만들어 넣는다. 서비스 밖으로는 나가지 않는다.
    // commSn 이 null 이면 전체 공개 글. 캐시 멤버 키는 postSn 문자열이다.
    record FeedPost(String postTyp, long postSn, long userSn,
                             String nickName, String authorNm,
                             String postTtl, String postCntnt, LocalDateTime frstCrtDt,
                             int viewCnt, int likeCnt, int cmtCnt,
                             Long commSn, String commNm, String commDsplNm, String pfpUrl) {}

    // ─────────────────────────────────────────────────────────────────────
    // 홈 피드 (전체 개인화)
    // ─────────────────────────────────────────────────────────────────────
    public ResultVO getPersonalizedFeed(Map<String, Object> params, Long me) {
        return buildFeed(params, me, "PERSONALIZED");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 팔로잉 피드
    // ─────────────────────────────────────────────────────────────────────
    public ResultVO getFollowingFeed(Map<String, Object> params, Long me) {
        return buildFeed(params, me, "FOLLOWING");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 인기 피드
    // ─────────────────────────────────────────────────────────────────────
    public ResultVO getPopularFeed(Map<String, Object> params, Long me) {
        return buildFeed(params, me, "POPULAR");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 최신 피드
    // ─────────────────────────────────────────────────────────────────────
    public ResultVO getLatestFeed(Map<String, Object> params, Long me) {
        return buildFeed(params, me, "LATEST");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 트렌딩 피드 — "지금 뜨는 글"
    // ─────────────────────────────────────────────────────────────────────
    /**
     * 인기(누적 반응 상위)·최신(시간순)과 다른 세 번째 축이다. <b>최근 글 가운데 반응이
     * 빠르게 붙는 글</b>을 올린다 — 반응 총량을 나이로 나눈 속도(Hacker News 식 중력 감쇠).
     * 오래 쌓인 글은 총량이 커도 나이에 눌려 내려가고, 몇 시간 된 글이 반응 몇 개만 받아도
     * 위로 올라온다. 개인화는 섞지 않는다 — 모두에게 같은 "지금"이다.
     */
    public ResultVO getTrendingFeed(Map<String, Object> params, Long me) {
        return buildFeed(params, me, "TRENDING");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 공통 피드 빌더
    // ─────────────────────────────────────────────────────────────────────
    private ResultVO buildFeed(Map<String, Object> params, Long me, String mode) {
        ResultVO resultVO = new ResultVO();
        try {
            Long userSn = me;
            int page = params.get("pageIndex") != null
                    ? Integer.parseInt(params.get("pageIndex").toString()) : 0;

            String cacheKey = buildCacheKey(mode, userSn);

            // ── Cache HIT ────────────────────────────────────────────────
            if (cacheKey != null && feedCacheService.exists(cacheKey)) {
                List<String>   members  = feedCacheService.getPage(cacheKey, page, PAGE_SIZE);
                long           total    = feedCacheService.size(cacheKey);
                List<FeedPost> items    = hydrateMembers(members, userSn);
                List<PostDTO>  list     = buildPostDtoList(items, userSn);
                Set<String> exploreSlots = "PERSONALIZED".equals(mode) && userSn != null
                        ? feedCacheService.getExploreSlots(feedCacheService.exploreSlotsKey(userSn))
                        : Set.of();
                applyFeedReasons(list, items, userSn, exploreSlots);

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

            Map<Long, List<Long>> tagSnMap = fetchPostTagSnMap(candidates.stream().map(FeedPost::postSn).toList());

            // 점수를 Map에 미리 계산 (정렬 + Redis 저장 공용)
            Map<String, Double> memberScores = new LinkedHashMap<>();
            candidates.forEach(p -> memberScores.put(
                    memberKey(p),
                    computeScore(p, mode, ranking, userTagScores, authorScores, commScores,
                            followedUserSns, joinedCommSns, tagSnMap)));

            List<FeedPost> scored = candidates.stream()
                    .sorted(Comparator.comparingDouble(p -> -memberScores.get(memberKey(p))))
                    .collect(Collectors.toList());

            // ── 탐험 슬롯 쿼터 ───────────────────────────────────────────
            // PERSONALIZED 에만 건다. 인기·최신은 "지금 뜨는 것/새로 올라온 것" 을 보러
            // 오는 화면이고, 팔로잉은 정의상 전부 아는 사람이라 낯선 글을 끼워 넣으면
            // 그 피드가 약속한 것을 어긴다.
            Set<String> exploreSlots = "PERSONALIZED".equals(mode)
                    ? applyExploreQuota(scored, ranking.getExploreSlotEvery(),
                            authorScores, commScores, followedUserSns, joinedCommSns)
                    : Set.of();

            // Redis 캐시 저장.
            // score 는 최종 순위를 인코딩한다 — 쿼터가 순서를 바꾸므로 원점수를 그대로
            // 넣으면 Redis 가 되읽을 때 쿼터 이전 순서로 돌아간다. 읽는 쪽은 순서만 쓴다.
            // TTL 은 전부 붙인다. 반응(좋아요·댓글)은 더 이상 캐시를 건드리지 않으므로
            // TTL 이 반응을 순위에 반영하는 유일한 수단이다.
            if (cacheKey != null) {
                Map<String, Double> rankScores = new LinkedHashMap<>();
                for (int i = 0; i < scored.size(); i++) {
                    FeedPost p = scored.get(i);
                    rankScores.put(memberKey(p), (double) (scored.size() - i));
                }
                feedCacheService.populate(cacheKey, rankScores);
                switch (mode) {
                    case "POPULAR", "LATEST", "TRENDING" -> feedCacheService.expireGlobal(cacheKey);
                    case "PERSONALIZED", "FOLLOWING" -> feedCacheService.expirePersonalized(cacheKey);
                    default -> { }
                }
                if ("PERSONALIZED".equals(mode) && userSn != null) {
                    feedCacheService.putExploreSlots(feedCacheService.exploreSlotsKey(userSn), exploreSlots);
                }
            }

            // 페이지네이션
            int from      = page * PAGE_SIZE;
            int to        = Math.min(from + PAGE_SIZE, scored.size());
            List<FeedPost> pageItems = from < scored.size() ? scored.subList(from, to) : List.of();

            List<PostDTO> pageList = buildPostDtoList(pageItems, userSn);
            applyFeedReasons(pageList, pageItems, userSn, exploreSlots);
            resultVO.putResult("list",     pageList);
            resultVO.putResult("totalCnt", scored.size());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());

        } catch (Exception e) {
            ErrorLog.report(e);
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
            case "TRENDING"     -> feedCacheService.trendingKey();
            default             -> null;
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // 캐시 멤버 → FeedPost (ID만 저장된 캐시에서 DB 배치 조회)
    // ─────────────────────────────────────────────────────────────────────
    /** 캐시 멤버 키. V2 이전의 "POST:1"/"COMM:1" 형식은 숫자가 아니라 아래에서 걸러진다. */
    static String memberKey(FeedPost p) { return String.valueOf(p.postSn()); }

    private static Long parseMember(String m) {
        try { return Long.parseLong(m); } catch (NumberFormatException e) { return null; }
    }

    private List<FeedPost> hydrateMembers(List<String> members, Long viewerSn) {
        List<Long> postSns = members.stream().map(FeedService::parseMember).filter(Objects::nonNull).toList();
        Map<Long, FeedPost> postMap = fetchPostsByIds(postSns, viewerSn).stream()
                .collect(Collectors.toMap(FeedPost::postSn, p -> p));

        // Redis 순서(score DESC) 유지
        return postSns.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // FeedPost 리스트 → PostDTO 리스트 (tagNms / 좋아요 / 저장 배치 조회)
    // ─────────────────────────────────────────────────────────────────────
    private List<PostDTO> buildPostDtoList(List<FeedPost> items, Long userSn) {
        List<PostDTO> list = new ArrayList<>(items.size());
        for (FeedPost p : items) {
            PostDTO dto = new PostDTO(p.postTyp(), p.postSn(),
                    p.commSn(), p.commNm(), p.commDsplNm(),
                    p.userSn(), p.nickName(), p.authorNm(),
                    p.postTtl(), p.postCntnt(), p.frstCrtDt(),
                    p.viewCnt(), p.likeCnt(), p.cmtCnt(),
                    null, false);
            dto.setPfpUrl(p.pfpUrl());
            list.add(dto);
        }
        return postQuery.decorateDtos(list, userSn);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 점수 계산
    // ─────────────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────
    // 피드 근거 — "왜 이 글이 보이는가"
    //
    // 점수는 5개 축의 가중합이라 총점만으로는 이유를 알 수 없다. 여기서 축별 기여도를
    // 다시 계산해 가장 큰 축 하나를 근거로 붙인다. 화면에 표시할 용도라 현재 페이지
    // (최대 PAGE_SIZE 건)에 대해서만 계산한다 — 캐시 히트 경로에서도 같은 값이 나온다.
    // ─────────────────────────────────────────────────────────────────────
    private void applyFeedReasons(List<PostDTO> list, List<FeedPost> items, Long userSn,
                                   Set<String> exploreSlots) {
        if (list.isEmpty() || items.isEmpty()) return;

        Set<Long> followedUserSns = userSn != null ? fetchFollowedUserSns(userSn) : Set.of();
        Set<Long> joinedCommSns   = userSn != null ? fetchJoinedCommSns(userSn)   : Set.of();
        Map<Long, Double> userTagScores = userSn != null ? buildTagScoreMap(userSn)    : Map.of();
        Map<Long, Double> authorScores  = userSn != null ? buildAuthorScoreMap(userSn) : Map.of();
        Map<Long, Double> commScores    = userSn != null ? buildCommScoreMap(userSn)   : Map.of();

        Map<Long, List<Long>> tagSnMap = fetchPostTagSnMap(items.stream().map(FeedPost::postSn).toList());

        // 맞은 태그의 이름을 보여주려면 SN -> 이름이 필요하다. 이 페이지에 나온 태그만 조회한다.
        Set<Long> tagSnsOnPage = new HashSet<>();
        tagSnMap.values().forEach(tagSnsOnPage::addAll);
        Map<Long, String> tagNames = tagSnsOnPage.isEmpty() ? Map.of()
                : tblTagRepository.findAllById(tagSnsOnPage).stream()
                    .collect(Collectors.toMap(TblTag::getTagSn, TblTag::getTagNm, (a, b) -> a));

        Map<Long, FeedPost> byKey = items.stream()
                .collect(Collectors.toMap(FeedPost::postSn, p -> p, (a, b) -> a));

        for (PostDTO dto : list) {
            FeedPost p = byKey.get(dto.getPostSn());
            if (p == null) continue;

            List<Long> tagSns = tagSnMap.getOrDefault(p.postSn(), List.of());

            // 정렬에 쓴 것과 같은 축 값을 쓴다. 정규화되어 있으므로 W 를 곱한 기여도끼리
            // 비교해도 된다.
            FeedAxes ax = computeAxes(p, ranking, userTagScores, authorScores, commScores,
                    followedUserSns, joinedCommSns, tagSns);

            // 쿼터로 끌어올린 글은 점수로 올라온 게 아니므로 축을 볼 필요가 없다.
            // 여기서만 EXPLORE 가 나온다 — 이제 "의도적으로 섞은 글" 이라는 진짜 근거다.
            if (exploreSlots.contains(String.valueOf(dto.getPostSn()))) {
                dto.setFeedReason("EXPLORE");
                dto.setFeedReasonDetail(null);
                continue;
            }

            // 총점에 가장 많이 기여한 축이 곧 근거다. 축이 정규화되어 있으므로 가중
            // 기여도(W × 축)끼리 그대로 비교하면 된다.
            //
            // 정규화 전에는 이렇게 할 수 없었다. 품질이 수백 단위, 나머지가 한 자릿수라
            // 최대값을 고르면 무조건 "인기" 가 나왔고, 그래서 팔로우·가입·관심사를 먼저
            // 보는 우선순위 방식으로 우회했다. 이제 그 우회가 필요 없다.
            double interest = ax.interest()  * ranking.getWeightInterest();
            double relation = ax.relation()  * ranking.getWeightRelation();
            double quality  = ax.quality()   * ranking.getWeightQuality();
            double fresh    = ax.freshness() * ranking.getWeightFreshness();

            double top = Math.max(Math.max(interest, relation), Math.max(quality, fresh));

            String reason; String detail = null;
            if (top == relation) {
                // 관계 축은 세 가지를 함께 담는다 — 팔로우, 커뮤니티 가입, 그리고 명시적
                // 관계 없이 행동으로 쌓인 친밀도. 어느 쪽이 근거인지 갈라 준다.
                // 사람을 고른 쪽이 가장 구체적인 설명이라 먼저 본다.
                if (followedUserSns.contains(p.userSn())) {
                    reason = "FOLLOWING";
                } else if (p.commSn() != null && joinedCommSns.contains(p.commSn())) {
                    reason = "JOINED_COMM";
                    detail = dto.getCommDsplNm();
                } else {
                    // 팔로우도 가입도 안 했는데 관계 축이 1위다 = 자주 보던 작성자·커뮤니티.
                    reason = "AFFINITY";
                    detail = p.commSn() != null ? dto.getCommDsplNm() : p.nickName();
                }
            } else if (top == interest) {
                reason = "INTEREST";
                detail = tagSns.stream()
                        .filter(t -> userTagScores.getOrDefault(t, 0.0) > 0)
                        .max(Comparator.comparingDouble(t -> userTagScores.getOrDefault(t, 0.0)))
                        .map(tagNames::get).orElse(null);
            } else if (top == fresh) {
                reason = "FRESH";
            } else {
                reason = "POPULAR";
            }
            dto.setFeedReason(reason);
            dto.setFeedReasonDetail(detail);
        }
    }

    /**
     * 정규화된 축 점수. 모든 값이 [0,1] 이라 서로 비교할 수 있다.
     *
     * <p>정렬용 총점(computeScore)과 근거 계산(applyFeedReasons)이 같은 값을 써야 하므로
     * 축 계산은 여기 한 곳에만 둔다. 예전에는 두 곳에 복사되어 있었다.
     */
    record FeedAxes(double interest, double relation, double quality, double freshness) {

        /** 가중합. 가중치 합이 1.0 이라 결과도 [0,1] 이다. */
        double total(FeedRankingProperties cfg) {
            return cfg.getWeightInterest()  * interest
                 + cfg.getWeightRelation()  * relation
                 + cfg.getWeightQuality()   * quality
                 + cfg.getWeightFreshness() * freshness;
        }
    }

    /**
     * 상한이 없는 원점수를 [0,1] 로 누른다.
     *
     * <p>조회수·좋아요처럼 상한이 없고 꼬리가 긴 값을 다루기 위한 것이다. 그냥 잘라내면
     * 상위권이 뭉개지고, 선형으로 나누면 이상치 하나가 축 전체를 지배한다. 로그는 둘 다
     * 피한다. {@code log1p} 라서 원점수 0 은 정확히 0 으로 남는다 — 신호 없는 글에
     * 바닥 점수를 깔아 주면 축이 상수가 되어 순위 정보를 잃는다.
     *
     * @param ref 1.0 에 수렴하기 시작하는 기준값
     */
    static double saturate(double raw, double ref) {
        if (raw <= 0) return 0.0;
        return Math.min(1.0, Math.log1p(raw) / Math.log1p(ref));
    }

    // 인스턴스 상태를 쓰지 않는 순수 함수라 static 이다. 덕분에 테스트가 서비스
    // 전체를 띄우지 않고 랭킹 수식만 검증할 수 있다.
    static FeedAxes computeAxes(FeedPost p,
                                 FeedRankingProperties cfg,
                                 Map<Long, Double> userTagScores,
                                 Map<Long, Double> authorScores,
                                 Map<Long, Double> commScores,
                                 Set<Long> followedUserSns,
                                 Set<Long> joinedCommSns,
                                 List<Long> tagSns) {

        // ── 관심사 (W1) ──────────────────────────────────────────────────
        // 태그 점수의 합. 태그 수 × 태그당 점수라 상한이 없다.
        double interestRaw = tagSns.stream()
                .mapToDouble(t -> userTagScores.getOrDefault(t, 0.0))
                .sum();
        double interest = saturate(interestRaw, cfg.getInterestRef());

        // ── 관계 (W3) ────────────────────────────────────────────────────
        // 구성 요소의 성격이 다르다. 팔로우·가입은 사용자가 명시적으로 선언한 관계이고,
        // 친밀도는 행동에서 추정한 값이다. 명시 신호에 바닥을 주고 친밀도로 올린다.
        boolean isFollowedAuthor = followedUserSns.contains(p.userSn());
        boolean isJoinedComm     = p.commSn() != null && joinedCommSns.contains(p.commSn());
        double  authorInterest   = authorScores.getOrDefault(p.userSn(), 0.0);
        double  commInterest     = p.commSn() != null ? commScores.getOrDefault(p.commSn(), 0.0) : 0.0;

        double explicitBond = (isFollowedAuthor ? 0.5 : 0.0) + (isJoinedComm ? 0.4 : 0.0);
        double affinity     = saturate(authorInterest + commInterest * 1.2, cfg.getRelationRef());
        double relation     = Math.min(1.0, explicitBond + affinity * 0.5);

        // ── 품질 (W4) ────────────────────────────────────────────────────
        // 누적 총량이 아니라 시간당 반응률이다. 총량으로 재면 6개월 된 글이 그동안 쌓은
        // 조회수를 영구히 유지해, 신선도 항을 더해도 되돌릴 수 없다. 반응률은 "지금
        // 반응이 오고 있는가" 를 재므로 나이가 다른 글을 같은 자로 비교할 수 있다.
        long   elapsedHours = Math.max(0, ChronoUnit.HOURS.between(p.frstCrtDt(), LocalDateTime.now()));
        double qualityRaw   = p.likeCnt() * 2.0 + p.cmtCnt() * 4.0 + p.viewCnt() * 0.2;
        double qualityRate  = qualityRaw / (elapsedHours + cfg.getQualityPriorHours());
        double quality      = saturate(qualityRate, cfg.getQualityRateRef());

        // ── 신선도 (W5) ──────────────────────────────────────────────────
        // 반감기로 쓴다. 기존 100/√(t+2) 도 감쇠 곡선이지만 눈금이 읽히지 않았다.
        double freshness = Math.pow(0.5, elapsedHours / cfg.getFreshHalfLifeHours());

        // ── 탐험 축은 없앴다 ─────────────────────────────────────────────
        // 두 단계에 걸쳐 죽은 축이라는 게 드러났다.
        //
        // 원래 식은 (1 - 친숙도) × novelty × 20 이었다. novelty 는 "내 태그와 안 맞을수록
        // 1" 이라 관심사 축의 부호만 뒤집은 값이었고(실측 상관 -0.9226), W1 이 올린 글을
        // W6 이 같은 크기로 끌어내려 태그 개인화의 순효과가 0 이었다. novelty 를 빼자
        // 남은 (1 - 친숙도) 는 112건 중 103건이 정확히 1.0, 서로 다른 값이 4개뿐이었다.
        // 거의 모든 글에 같은 값을 더하므로 순위를 바꾸지 않으면서 예산 15% 를 쓰고,
        // 근거 계산에서는 112건 중 96건의 최대 기여 축이 되어 칩을 무의미하게 만들었다.
        //
        // 탐험은 점수 축으로는 성립하지 않는다 — 값이 고르면 순위를 안 바꾸고, 값이
        // 갈리면 관련도와 정면으로 싸운다. 제대로 하려면 슬롯 쿼터여야 한다:
        // 한 페이지 50건 중 N건을 한 번도 엮인 적 없는 작성자·커뮤니티에 고정 배정한다.
        // 탐험량을 직접 통제할 수 있고 관련도 점수를 훼손하지 않는다.
        // 얼마나 섞을지는 제품 결정이라 여기서 정하지 않았다 (docs/feed-ranking.md §4.5).

        return new FeedAxes(interest, relation, quality, freshness);
    }

    static double computeScore(FeedPost p, String mode,
                                FeedRankingProperties cfg,
                                Map<Long, Double> userTagScores,
                                Map<Long, Double> authorScores,
                                Map<Long, Double> commScores,
                                Set<Long> followedUserSns,
                                Set<Long> joinedCommSns,
                                Map<Long, List<Long>> tagSnMap) {

        FeedAxes ax = computeAxes(p, cfg, userTagScores, authorScores, commScores,
                followedUserSns, joinedCommSns, tagSnMap.getOrDefault(p.postSn(), List.of()));

        double totalScore = ax.total(cfg);

        // 모드 보정도 정규화된 값으로만 한다. 보정항에 원점수를 섞으면 총점이 [0,1] 인
        // 지금 그 항 하나가 순위를 통째로 지배한다 — 정규화와 반드시 같이 가야 하는 변경.
        boolean isFollowedAuthor = followedUserSns.contains(p.userSn());
        boolean isJoinedComm     = p.commSn() != null && joinedCommSns.contains(p.commSn());

        return switch (mode) {
            // 보정항의 0.6/0.4 는 관계 축의 0.5/0.4 와 일부러 다르다. 관계 축은 뒤에
            // 친밀도가 더해지므로 0.9 에서 멈추지만, 여기는 단독으로 쓰이는 [0,1] 항이다.
            case "FOLLOWING" -> totalScore * 0.7
                    + ((isFollowedAuthor ? 0.6 : 0.0) + (isJoinedComm ? 0.4 : 0.0)) * 0.3;
            case "POPULAR"   -> totalScore * 0.6 + ax.quality()   * 0.4;
            case "LATEST"    -> totalScore * 0.7 + ax.freshness() * 0.3;
            case "TRENDING"  -> trendingScore(p);
            default          -> totalScore; // PERSONALIZED
        };
    }

    /**
     * 트렌딩 점수 = 반응 / (나이시간 + 2)^1.3.
     *
     * <p>댓글이 좋아요보다, 좋아요가 조회보다 무겁다 — 조회는 노출만 있어도 쌓이지만
     * 댓글은 손이 가야 한다. +2 는 막 올라온 글이 반응 하나로 1위가 되는 걸 막는 완충,
     * 지수 1.3 은 "하루 지난 글은 같은 반응으로 새 글의 1/7" 쯤 되게 하는 값이다.
     * 개인화 축을 전혀 섞지 않으므로 정규화할 필요가 없다(이 모드에서 단독으로 쓰인다).
     */
    static double trendingScore(FeedPost p) {
        long   hours   = Math.max(0, ChronoUnit.HOURS.between(p.frstCrtDt(), LocalDateTime.now()));
        double engaged = p.likeCnt() * 3.0 + p.cmtCnt() * 5.0 + p.viewCnt() * 0.05;
        return engaged / Math.pow(hours + 2.0, 1.3);
    }

    /**
     * 한 번도 엮인 적 없는 글인가 — 탐험 쿼터의 대상.
     *
     * <p>팔로우도 가입도 안 했고, 행동으로 쌓인 친밀도도 0 인 작성자·커뮤니티의 글이다.
     * 관계 축이 정확히 이 세 가지의 합이므로 {@code relation == 0} 과 같은 조건이지만,
     * 부동소수 비교에 의미를 얹지 않으려고 따로 판단한다.
     */
    static boolean isUnfamiliar(FeedPost p,
                                 Map<Long, Double> authorScores,
                                 Map<Long, Double> commScores,
                                 Set<Long> followedUserSns,
                                 Set<Long> joinedCommSns) {
        if (followedUserSns.contains(p.userSn())) return false;
        if (p.commSn() != null && joinedCommSns.contains(p.commSn())) return false;
        if (authorScores.getOrDefault(p.userSn(), 0.0) > 0) return false;
        return p.commSn() == null || commScores.getOrDefault(p.commSn(), 0.0) <= 0;
    }

    /**
     * 탐험 슬롯 쿼터 — {@code slotEvery} 건마다 최소 1건은 낯선 글이 오도록 끌어올린다.
     *
     * <p>탐험을 점수 축으로 두면 작동하지 않는다. 값이 고르면 순위를 안 바꾸고, 값이
     * 갈리면 관련도와 정면으로 싸운다(docs/feed-ranking.md §2). 쿼터는 그 둘을 피한다 —
     * 탐험량을 직접 정하면서 나머지 글의 점수는 건드리지 않는다.
     *
     * <p><b>하한이라 대개 아무 일도 하지 않는다.</b> 이미 낯선 글이 섞여 있는 블록은
     * 그냥 넘어간다. 실측(49명)에서 상위 50건 중 익숙한 글은 중앙값 13건이라 3명에게만
     * 걸렸다 — 그 3명이 40건 이상 익숙한 글로 채워져 닫혀 가던 사용자다.
     *
     * <p>끌어올리는 글은 무작위가 아니라 <b>낯선 글 중 점수가 가장 높은 것</b> 이다.
     * 이미 매긴 순위를 따르므로 품질이 떨어지지 않는다. 나머지 글의 상대 순서는 그대로다.
     *
     * @return 쿼터로 끌어올린 글의 멤버 키. 근거를 EXPLORE 로 표시하는 데 쓴다.
     */
    static Set<String> applyExploreQuota(List<FeedPost> sorted,
                                          int slotEvery,
                                          Map<Long, Double> authorScores,
                                          Map<Long, Double> commScores,
                                          Set<Long> followedUserSns,
                                          Set<Long> joinedCommSns) {
        if (slotEvery <= 0 || sorted.size() <= slotEvery) return Set.of();

        boolean[] unfamiliar = new boolean[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            unfamiliar[i] = isUnfamiliar(sorted.get(i), authorScores, commScores,
                    followedUserSns, joinedCommSns);
        }

        Set<String> promoted = new LinkedHashSet<>();

        for (int blockStart = 0; blockStart + slotEvery <= sorted.size(); blockStart += slotEvery) {
            int blockEnd = blockStart + slotEvery;

            boolean satisfied = false;
            for (int i = blockStart; i < blockEnd && !satisfied; i++) satisfied = unfamiliar[i];
            if (satisfied) continue;

            // 블록 밖에서 가장 점수가 높은(= 가장 앞에 있는) 낯선 글을 찾는다.
            int donor = -1;
            for (int i = blockEnd; i < sorted.size(); i++) {
                if (unfamiliar[i]) { donor = i; break; }
            }
            if (donor < 0) break;   // 남은 낯선 글이 없다. 더 볼 필요 없다.

            // 블록의 마지막 자리로 옮기고, 사이의 글들을 한 칸씩 뒤로 민다.
            FeedPost item = sorted.remove(donor);
            sorted.add(blockEnd - 1, item);
            System.arraycopy(unfamiliar, blockEnd - 1, unfamiliar, blockEnd, donor - blockEnd + 1);
            unfamiliar[blockEnd - 1] = true;

            promoted.add(memberKey(item));
        }
        return promoted;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 후보 수집
    // ─────────────────────────────────────────────────────────────────────
    private List<FeedPost> collectCandidates(Long userSn, Set<Long> followedUserSns,
                                               Set<Long> joinedCommSns, String mode) {
        Set<Long>      dedup  = new LinkedHashSet<>();
        List<FeedPost> result = new ArrayList<>();

        // 글 모델을 합치기 전에는 아래 호출이 전부 일반 글 / 커뮤니티 글 두 벌이었고,
        // 각각 MAX_CANDIDATES / 2 씩 가져와 합쳤다. 이제 한 쿼리가 둘 다 가져온다.
        if ("FOLLOWING".equals(mode)) {
            // 팔로잉 피드: 팔로우한 유저가 작성한 게시글만
            if (!followedUserSns.isEmpty()) {
                fetchPostsByUsers(followedUserSns, userSn).stream()
                        .filter(p -> dedup.add(p.postSn()))
                        .forEach(result::add);
            }
            return result.stream().limit(MAX_CANDIDATES).collect(Collectors.toList());
        }

        if ("POPULAR".equals(mode)) {
            // 인기 피드: 반응 수 기준 상위 게시글 (날짜 무관)
            fetchTopPostsByQuality(MAX_CANDIDATES, userSn).stream()
                    .filter(p -> dedup.add(p.postSn()))
                    .forEach(result::add);
            return result.stream().limit(MAX_CANDIDATES).collect(Collectors.toList());
        }

        if ("TRENDING".equals(mode)) {
            // 트렌딩: 최근 글 가운데 반응이 하나라도 있는 글. 창을 48시간 → 7일 → 30일 → 90일로
            // 넓혀 가며 20건이 모이면 멈춘다 — 조용한 날에도 탭이 비지 않게, 바쁜 날엔 진짜 "지금"만.
            // 마지막 36500일은 사실상 "전체" — 반응 있는 글이 90일 안에 하나도 없는 조용한 사이트에서도
            // 탭이 비어 보이지 않게 한다.
            for (int days : new int[]{2, 7, 30, 90, 36500}) {
                result.clear(); dedup.clear();
                fetchEngagedPostsSince(LocalDateTime.now().minusDays(days), MAX_CANDIDATES, userSn).stream()
                        .filter(p -> dedup.add(p.postSn()))
                        .forEach(result::add);
                if (result.size() >= 20) break;
            }
            return result.stream().limit(MAX_CANDIDATES).collect(Collectors.toList());
        }

        if ("LATEST".equals(mode)) {
            // 최신 피드: 전체 최신 게시글 500개
            fetchLatestPosts(MAX_CANDIDATES, userSn).stream()
                    .filter(p -> dedup.add(p.postSn()))
                    .forEach(result::add);
            return result.stream().limit(MAX_CANDIDATES).collect(Collectors.toList());
        }

        // PERSONALIZED: 팔로우한 유저의 게시글 (날짜 제한 없음)
        if (!followedUserSns.isEmpty()) {
            fetchPostsByUsers(followedUserSns, userSn).stream()
                    .filter(p -> dedup.add(p.postSn()))
                    .forEach(result::add);
        }

        // 가입한 커뮤니티 게시글 (날짜 제한 없음)
        if (!joinedCommSns.isEmpty()) {
            fetchPostsByComms(joinedCommSns, userSn).stream()
                    .filter(p -> dedup.add(p.postSn()))
                    .forEach(result::add);
        }

        // 관심 태그 기반 게시글 (90일 이내)
        if (userSn != null) {
            List<Long> interestedTagSns = fetchInterestedTagSns(userSn);
            if (!interestedTagSns.isEmpty()) {
                LocalDateTime since90 = LocalDateTime.now().minusDays(90);
                fetchPostsByTags(interestedTagSns, since90, userSn).stream()
                        .filter(p -> dedup.add(p.postSn()))
                        .forEach(result::add);
            }
        }

        // fallback: 비회원이거나 후보 50개 미만이면 최신 게시글로 보충
        if (userSn == null || result.size() < 50) {
            fetchLatestPosts(200, userSn).stream()
                    .filter(p -> dedup.add(p.postSn()))
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
    // 후보 게시글 조회 — 전부 PostQuery.base() 위에 조건·정렬만 얹는다
    // ─────────────────────────────────────────────────────────────────────
    private List<FeedPost> fetchPostsByUsers(Set<Long> userSns, Long viewerSn) {
        return toFeedPosts(postQuery.base()
                .where(POST.userSn.in(userSns), PostQuery.alive(), PostQuery.visibleTo(viewerSn),
                        PostQuery.notAnonymous())   // 팔로우한 사람의 익명 글을 「팔로우」 이유로 모으면 누가 썼는지 드러난다
                .orderBy(POST.frstCrtDt.desc())
                .fetch());
    }

    /** 가입한 커뮤니티의 글. 접근은 되지만 차단·숨김은 여기서도 빼야 한다(visibleTo) — 예전엔 빠져 있었다. */
    private List<FeedPost> fetchPostsByComms(Set<Long> commSns, Long viewerSn) {
        return toFeedPosts(postQuery.base()
                .where(POST.commSn.in(commSns), PostQuery.alive(), PostQuery.visibleTo(viewerSn))
                .orderBy(POST.frstCrtDt.desc())
                .fetch());
    }

    private List<FeedPost> fetchPostsByTags(List<Long> tagSns, LocalDateTime since, Long viewerSn) {
        QTblPostTag qTag = QTblPostTag.tblPostTag;
        return toFeedPosts(postQuery.base()
                .join(qTag).on(qTag.postSn.eq(POST.postSn).and(qTag.tagSn.in(tagSns)))
                .where(PostQuery.alive(), POST.frstCrtDt.goe(since), PostQuery.visibleTo(viewerSn))
                .distinct()
                .orderBy(POST.frstCrtDt.desc())
                .fetch());
    }

    // ID 배치 조회 (캐시 히트 시 사용)
    private List<FeedPost> fetchPostsByIds(List<Long> postSns, Long viewerSn) {
        if (postSns.isEmpty()) return List.of();
        return toFeedPosts(postQuery.base()
                .where(POST.postSn.in(postSns), PostQuery.alive(), PostQuery.visibleTo(viewerSn))
                .fetch());
    }

    // likeCnt*2 + cmtCnt*4 + viewCnt*0.2 순 (DB 정렬은 정수 근사)
    private List<FeedPost> fetchTopPostsByQuality(int limit, Long viewerSn) {
        return toFeedPosts(postQuery.base()
                .where(PostQuery.alive(), PostQuery.visibleTo(viewerSn))
                .orderBy(POST.likeCnt.multiply(2).add(POST.cmtCnt.multiply(4)).desc())
                .limit(limit)
                .fetch());
    }

    /** since 이후 글 가운데 좋아요나 댓글이 하나라도 있는 글. 조회만 있는 글은 트렌딩 후보가 아니다. */
    private List<FeedPost> fetchEngagedPostsSince(LocalDateTime since, int limit, Long viewerSn) {
        return toFeedPosts(postQuery.base()
                .where(PostQuery.alive(), PostQuery.visibleTo(viewerSn),
                        POST.frstCrtDt.goe(since),
                        POST.likeCnt.gt(0).or(POST.cmtCnt.gt(0)))
                .orderBy(POST.frstCrtDt.desc())
                .limit(limit)
                .fetch());
    }

    private List<FeedPost> fetchLatestPosts(int limit, Long viewerSn) {
        return toFeedPosts(postQuery.base()
                .where(PostQuery.alive(), PostQuery.visibleTo(viewerSn))
                .orderBy(POST.frstCrtDt.desc())
                .limit(limit)
                .fetch());
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
    private static List<FeedPost> toFeedPosts(List<com.querydsl.core.Tuple> tuples) {
        List<FeedPost> list = new ArrayList<>(tuples.size());
        for (com.querydsl.core.Tuple t : tuples) {
            TblPost p = t.get(POST);
            list.add(new FeedPost(p.getPostTyp(),
                    Optional.ofNullable(p.getPostSn()).orElse(0L), p.getUserSn(),
                    t.get(PostQuery.USER.nickName), t.get(PostQuery.USER.name),
                    p.getPostTtl(), p.getPostCntnt(), p.getFrstCrtDt(),
                    p.getViewCnt(), p.getLikeCnt(), p.getCmtCnt(),
                    p.getCommSn(), t.get(PostQuery.COMM.commNm), t.get(PostQuery.COMM.commDsplNm),
                    PostQuery.pfpUrl(t)));
        }
        return list;
    }
}
