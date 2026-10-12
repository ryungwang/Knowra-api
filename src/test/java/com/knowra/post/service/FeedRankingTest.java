package com.knowra.post.service;

import com.knowra.post.config.FeedRankingProperties;
import com.knowra.post.service.FeedService.FeedAxes;
import com.knowra.post.service.FeedService.FeedPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 랭킹 수식의 불변식.
 *
 * <p>정규화는 "돌아간다/안 돈다" 가 아니라 "분포가 의도대로인가" 의 문제라, 축이 자기
 * 가중치보다 큰 지분을 먹기 시작해도 기능은 멀쩡히 동작한다. 그래서 여기서 재는 것은
 * 결과값이 아니라 <b>척도</b> 다. 정규화가 깨지면 이 테스트가 먼저 걸린다.
 *
 * <p>배경과 측정 절차: docs/feed-ranking.md
 */
class FeedRankingTest {

    /** 기본값 그대로의 설정. 각 테스트가 필요하면 세터로 바꿔 쓴다. */
    private static final FeedRankingProperties CFG = new FeedRankingProperties();

    private static final double W1 = 0.18, W3 = 0.29, W4 = 0.29, W5 = 0.24;

    // ─────────────────────────────────────────────────────────────────────
    // saturate — 상한 없는 원점수를 [0,1] 로 누르는 변환
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saturate: 원점수 0 은 정확히 0 으로 남는다")
    void saturateKeepsZeroAtZero() {
        // 신호 없는 글에 바닥 점수를 깔아 주면 축이 상수가 되어 순위 정보를 잃는다.
        assertThat(FeedService.saturate(0.0, 20.0)).isEqualTo(0.0);
        assertThat(FeedService.saturate(-5.0, 20.0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("saturate: 단조 증가하고 절대 1 을 넘지 않는다")
    void saturateIsMonotonicAndBounded() {
        double prev = -1;
        for (double raw = 0; raw <= 100_000; raw = raw * 1.7 + 1) {
            double v = FeedService.saturate(raw, 20.0);
            assertThat(v).isBetween(0.0, 1.0);
            assertThat(v).isGreaterThanOrEqualTo(prev);
            prev = v;
        }
    }

    @Test
    @DisplayName("saturate: 기준값에서 1 에 도달한다")
    void saturateReachesOneAtReference() {
        assertThat(FeedService.saturate(20.0, 20.0)).isCloseTo(1.0, within(1e-9));
        assertThat(FeedService.saturate(1e9, 20.0)).isEqualTo(1.0);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 상한 불변식 — 가중치가 실제로 지분 상한인가
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("어떤 입력에서도 각 축은 [0,1], 총점은 [0,1] 안에 있다")
    void everyAxisStaysNormalized() {
        Random rnd = new Random(20260923L);

        for (int i = 0; i < 3_000; i++) {
            // 말이 안 되는 값까지 일부러 섞는다 — 조회수 폭주, 6년 된 글, 방금 올린 글.
            FeedPost p = post(
                    rnd.nextInt(2) == 0 ? "POST" : "COMM",
                    rnd.nextInt(1_000_000),       // view
                    rnd.nextInt(100_000),         // like
                    rnd.nextInt(50_000),          // cmt
                    rnd.nextInt(52_560));         // 경과 시간(최대 6년)

            FeedAxes ax = FeedService.computeAxes(p, CFG,
                    Map.of(1L, rnd.nextDouble() * 500, 2L, rnd.nextDouble() * 500),
                    Map.of(7L, rnd.nextDouble() * 500),
                    Map.of(9L, rnd.nextDouble() * 500),
                    rnd.nextBoolean() ? Set.of(7L) : Set.of(),
                    rnd.nextBoolean() ? Set.of(9L) : Set.of(),
                    List.of(1L, 2L));

            assertThat(ax.interest()).isBetween(0.0, 1.0);
            assertThat(ax.relation()).isBetween(0.0, 1.0);
            assertThat(ax.quality()).isBetween(0.0, 1.0);
            assertThat(ax.freshness()).isBetween(0.0, 1.0);
            assertThat(ax.total(CFG)).isBetween(0.0, 1.0);
        }
    }

    @Test
    @DisplayName("한 축의 기여는 자기 가중치를 넘지 못한다")
    void axisContributionNeverExceedsItsWeight() {
        // 모든 축을 최대로 밀어 올린 글. 정규화가 깨지면 여기서 즉시 드러난다.
        FeedPost p = post("COMM", 10_000_000, 10_000_000, 10_000_000, 0);

        FeedAxes ax = FeedService.computeAxes(p, CFG,
                Map.of(1L, 1e9), Map.of(7L, 1e9), Map.of(9L, 1e9),
                Set.of(7L), Set.of(9L), List.of(1L));

        assertThat(ax.interest() * W1).isLessThanOrEqualTo(W1);
        assertThat(ax.relation() * W3).isLessThanOrEqualTo(W3);
        assertThat(ax.quality() * W4).isLessThanOrEqualTo(W4);
        assertThat(ax.freshness() * W5).isLessThanOrEqualTo(W5);
        assertThat(ax.total(CFG)).isLessThanOrEqualTo(1.0);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 모드 보정식 — 정규화와 반드시 함께 가야 하는 변경
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("모드 보정 후에도 점수가 [0,1] 을 벗어나지 않는다")
    void modeAdjustedScoresStayNormalized() {
        // 보정항에 원점수(15, 20, 수백)가 하나라도 남아 있으면 총점이 [0,1] 인 지금
        // 그 항이 순위를 통째로 지배한다. 여기서 상한을 넘으며 걸린다.
        FeedPost p = post("COMM", 500_000, 90_000, 40_000, 0);
        Map<Long, List<Long>> tags = Map.of(p.postSn(), List.of(1L));

        for (String mode : List.of("PERSONALIZED", "FOLLOWING", "POPULAR", "LATEST")) {
            double score = FeedService.computeScore(p, mode, CFG,
                    Map.of(1L, 1e9), Map.of(7L, 1e9), Map.of(9L, 1e9),
                    Set.of(7L), Set.of(9L), tags);

            assertThat(score).as("mode=%s", mode).isBetween(0.0, 1.0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 품질 — 누적 총량이 아니라 시간당 반응률
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("반응이 지금 오는 새 글이, 누적만 많은 오래된 글보다 품질이 높다")
    void qualityMeasuresRateNotLifetimeTotal() {
        // 6개월 동안 12,000 점을 모은 글 vs 2시간 만에 200 점을 모은 글.
        // 누적 총량으로 재면 전자가 60배 앞선다 — 그게 개인화 피드를 인기 피드로
        // 만들었던 원인이다.
        FeedAxes old   = axes(post("POST", 50_000, 500, 250, 4_320));
        FeedAxes fresh = axes(post("POST",    800,  30,  15,     2));

        assertThat(fresh.quality()).isGreaterThan(old.quality());
    }

    @Test
    @DisplayName("갓 올라온 글의 반응 1건이 품질을 1.0 으로 만들지 않는다")
    void brandNewPostDoesNotExplodeOnFirstReaction() {
        // 분모 바닥(QUALITY_PRIOR_HOURS)이 없으면 경과 0시간에서 나눗셈이 발산해
        // 신규 글이 영구히 1등을 한다.
        FeedAxes ax = axes(post("POST", 0, 1, 0, 0));
        assertThat(ax.quality()).isLessThan(0.5);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 신선도 — 반감기
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("신선도는 반감기마다 절반이 된다")
    void freshnessHalvesEveryHalfLife() {
        assertThat(axes(post("POST", 0, 0, 0,  0)).freshness()).isCloseTo(1.00, within(1e-9));
        assertThat(axes(post("POST", 0, 0, 0, 24)).freshness()).isCloseTo(0.50, within(1e-9));
        assertThat(axes(post("POST", 0, 0, 0, 48)).freshness()).isCloseTo(0.25, within(1e-9));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 관계 — 명시적으로 선언한 관계가 바닥을 만든다
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("팔로우·가입은 친밀도 없이도 관계 점수의 바닥을 만든다")
    void explicitBondsGiveRelationAFloor() {
        FeedPost p = post("COMM", 0, 0, 0, 10);

        double none     = rel(p, Set.of(),   Set.of());
        double follow   = rel(p, Set.of(7L), Set.of());
        double join     = rel(p, Set.of(),   Set.of(9L));
        double both     = rel(p, Set.of(7L), Set.of(9L));

        assertThat(none).isEqualTo(0.0);
        assertThat(follow).isGreaterThan(join);   // 사람을 고른 쪽이 더 강한 의사표시
        assertThat(both).isCloseTo(0.9, within(1e-9));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 신호가 전혀 없는 사용자 (콜드 스타트)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("신호가 없는 새 사용자도 품질·신선도로 순서가 갈린다")
    void coldStartStillProducesAnOrdering() {
        FeedPost hot  = post("POST", 3_000, 200, 80, 3);
        FeedPost cold = post("POST",    10,   0,  0, 3);

        double hotScore  = FeedService.computeAxes(hot,  CFG, Map.of(), Map.of(), Map.of(),
                Set.of(), Set.of(), List.of()).total(CFG);
        double coldScore = FeedService.computeAxes(cold, CFG, Map.of(), Map.of(), Map.of(),
                Set.of(), Set.of(), List.of()).total(CFG);

        assertThat(hotScore).isGreaterThan(coldScore);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 가중치 합 검증 — 정규화의 전제
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("가중치 합이 1.0 이 아니면 기동을 막는다")
    void weightsMustSumToOne() {
        FeedRankingProperties bad = new FeedRankingProperties();
        bad.setWeightQuality(0.50);   // 합 1.25

        assertThatThrownBy(bad::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("가중치 합");
    }

    @Test
    @DisplayName("기본 설정은 합이 정확히 1.0 이다")
    void defaultWeightsAreValid() {
        FeedRankingProperties cfg = new FeedRankingProperties();
        assertThat(cfg.getWeightInterest() + cfg.getWeightRelation()
                + cfg.getWeightQuality() + cfg.getWeightFreshness()).isCloseTo(1.0, within(1e-9));
        cfg.validate();   // 예외 없이 통과해야 한다
    }

    // ─────────────────────────────────────────────────────────────────────
    // 탐험 슬롯 쿼터 — 10건마다 1건은 낯선 글
    // ─────────────────────────────────────────────────────────────────────

    /** 익숙한 글(작성자 7번을 팔로우) */
    private static FeedPost familiarPost(long sn) {
        return new FeedPost("NORMAL", sn, 7L, "닉", "이름", "제목", "본문",
                LocalDateTime.now().minusHours(5), 10, 1, 1, null, null, null, null);
    }

    /** 낯선 글 — 작성자도 커뮤니티도 엮인 적 없다. */
    private static FeedPost strangePost(long sn) {
        return new FeedPost("NORMAL", sn, 900L + sn, "낯", "낯선이", "제목", "본문",
                LocalDateTime.now().minusHours(5), 10, 1, 1, null, null, null, null);
    }

    private static Set<String> quota(List<FeedPost> list, int every) {
        return FeedService.applyExploreQuota(list, every, Map.of(), Map.of(),
                Set.of(7L), Set.of());
    }

    @Test
    @DisplayName("익숙한 글로만 찬 블록에는 낯선 글이 끌려 올라온다")
    void quotaPromotesIntoASaturatedBlock() {
        // 앞 20건은 전부 익숙, 그 뒤에 낯선 글.
        List<FeedPost> feed = new ArrayList<>();
        for (long i = 0; i < 20; i++) feed.add(familiarPost(i));
        for (long i = 20; i < 30; i++) feed.add(strangePost(i));

        Set<String> promoted = quota(feed, 10);

        assertThat(promoted).hasSize(2);
        // 블록의 마지막 자리(10번째, 20번째)에 들어간다.
        assertThat(feed.get(9).userSn()).isNotEqualTo(7L);
        assertThat(feed.get(19).userSn()).isNotEqualTo(7L);
    }

    @Test
    @DisplayName("점수가 가장 높은 낯선 글부터 끌어올린다")
    void quotaPromotesTheHighestScoringStranger() {
        List<FeedPost> feed = new ArrayList<>();
        for (long i = 0; i < 10; i++) feed.add(familiarPost(i));
        feed.add(strangePost(100));   // 11번째 = 낯선 글 중 가장 앞 = 가장 높은 점수
        feed.add(strangePost(101));

        quota(feed, 10);

        assertThat(feed.get(9).postSn()).isEqualTo(100L);
    }

    @Test
    @DisplayName("이미 낯선 글이 있는 블록은 건드리지 않는다")
    void quotaLeavesDiverseBlocksAlone() {
        List<FeedPost> feed = new ArrayList<>();
        for (long i = 0; i < 10; i++) feed.add(i == 3 ? strangePost(i) : familiarPost(i));
        for (long i = 10; i < 20; i++) feed.add(strangePost(i));
        List<Long> before = feed.stream().map(FeedPost::postSn).toList();

        Set<String> promoted = quota(feed, 10);

        assertThat(promoted).isEmpty();
        assertThat(feed.stream().map(FeedPost::postSn).toList()).isEqualTo(before);
    }

    @Test
    @DisplayName("끌어올린 글 말고는 서로의 순서가 그대로다")
    void quotaPreservesRelativeOrderOfEveryoneElse() {
        List<FeedPost> feed = new ArrayList<>();
        for (long i = 0; i < 15; i++) feed.add(familiarPost(i));
        for (long i = 15; i < 25; i++) feed.add(strangePost(i));

        Set<String> promoted = quota(feed, 10);

        List<Long> rest = feed.stream()
                .filter(p -> !promoted.contains(String.valueOf(p.postSn())))
                .map(FeedPost::postSn).toList();
        assertThat(rest).isSorted();   // 원래 0,1,2... 순서였다
    }

    @Test
    @DisplayName("낯선 글이 아예 없으면 아무 일도 하지 않는다")
    void quotaIsANoOpWithoutStrangers() {
        List<FeedPost> feed = new ArrayList<>();
        for (long i = 0; i < 30; i++) feed.add(familiarPost(i));

        assertThat(quota(feed, 10)).isEmpty();
        assertThat(feed).hasSize(30);
    }

    @Test
    @DisplayName("간격이 0 이하면 꺼진다")
    void quotaCanBeDisabled() {
        List<FeedPost> feed = new ArrayList<>();
        for (long i = 0; i < 30; i++) feed.add(familiarPost(i));
        feed.add(strangePost(99));

        assertThat(quota(feed, 0)).isEmpty();
        assertThat(feed.get(9).userSn()).isEqualTo(7L);
    }

    @Test
    @DisplayName("낯섦 판정: 팔로우·가입·친밀도 중 하나라도 있으면 낯설지 않다")
    void unfamiliarMeansNoTieAtAll() {
        FeedPost p = post("COMM", 10, 1, 1, 5);   // 작성자 7, 커뮤니티 9

        assertThat(FeedService.isUnfamiliar(p, Map.of(), Map.of(), Set.of(), Set.of())).isTrue();
        assertThat(FeedService.isUnfamiliar(p, Map.of(), Map.of(), Set.of(7L), Set.of())).isFalse();
        assertThat(FeedService.isUnfamiliar(p, Map.of(), Map.of(), Set.of(), Set.of(9L))).isFalse();
        assertThat(FeedService.isUnfamiliar(p, Map.of(7L, 3.0), Map.of(), Set.of(), Set.of())).isFalse();
        assertThat(FeedService.isUnfamiliar(p, Map.of(), Map.of(9L, 3.0), Set.of(), Set.of())).isFalse();
    }

    // ── 픽스처 ────────────────────────────────────────────────────────────

    private static FeedPost post(String kind, int view, int like, int cmt, int ageHours) {
        return new FeedPost("NORMAL", 100L, 7L, "닉", "이름", "제목", "본문",
                LocalDateTime.now().minusHours(ageHours).minusMinutes(1),
                view, like, cmt,
                "COMM".equals(kind) ? 9L : null, "커뮤", "커뮤니티", null);
    }

    private static FeedAxes axes(FeedPost p) {
        return FeedService.computeAxes(p, CFG, Map.of(), Map.of(), Map.of(),
                Set.of(), Set.of(), List.of());
    }

    private static double rel(FeedPost p, Set<Long> followed, Set<Long> joined) {
        return FeedService.computeAxes(p, CFG, Map.of(), Map.of(), Map.of(),
                followed, joined, List.of()).relation();
    }
}
