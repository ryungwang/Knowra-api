package com.knowra.post.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 피드 랭킹 상수.
 *
 * <p>코드에 박아 두지 않는 이유는 단순하다 — <b>랭킹 상수는 반드시 여러 번 고치게 된다.</b>
 * 기준값은 원칙적으로 해당 원점수의 p95 이고, 트래픽이 쌓이면 달라진다.
 * 재보정 절차와 근거는 {@code docs/feed-ranking.md}.
 *
 * <p>기본값은 합성 시드({@code src/docs/seed_data.py}) 기준의 출발점이지 결론이 아니다.
 */
@Component
@ConfigurationProperties(prefix = "knowra.feed.ranking")
public class FeedRankingProperties {

    /**
     * 가중치. 네 축이 모두 [0,1] 로 정규화되므로 이 값이 곧 그 축의 상한 지분이다.
     *
     * <p>탐험 축(0.15)을 제거하면서 나머지에 비례 배분한 값이다 — 축 하나를 뺀 것이지
     * 균형을 다시 잡은 게 아니라, 서로의 비율은 그대로 유지했다.
     */
    private double weightInterest  = 0.18;
    private double weightRelation  = 0.29;
    private double weightQuality   = 0.29;
    private double weightFreshness = 0.24;

    /** 각 원점수가 1.0 에 수렴하기 시작하는 지점. */
    private double interestRef = 10.0;
    private double relationRef = 20.0;
    private double qualityRateRef = 20.0;

    /**
     * 품질 반응률의 분모 바닥(시간).
     * 품질은 누적 총량이 아니라 시간당 반응률이라 분모가 경과시간이다. 이 바닥이 없으면
     * 갓 올라온 글의 좋아요 1개가 1/0 이 되어 신규 글이 영구히 1등을 한다.
     */
    private double qualityPriorHours = 4.0;

    /** 신선도 반감기(시간). 토론형 커뮤니티면 늘리고, 속보형이면 줄인다. */
    private double freshHalfLifeHours = 24.0;

    /**
     * 가중치 합이 1.0 이어야 총점이 [0,1] 에 들어온다.
     *
     * <p>이게 깨지면 정규화의 전제가 통째로 무너지는데, 겉으로는 멀쩡히 동작한다 —
     * 순위도 나오고 오류도 안 난다. 그래서 기동 시점에 막는다. 설정으로 뺀 이상
     * 누군가 한 축만 올리고 끝내는 일은 반드시 생긴다.
     */
    @PostConstruct
    public void validate() {
        double sum = weightInterest + weightRelation + weightQuality + weightFreshness;
        if (Math.abs(sum - 1.0) > 1e-9) {
            throw new IllegalStateException(
                    "knowra.feed.ranking 의 가중치 합이 1.0 이 아닙니다 (현재 %.4f). "
                    .formatted(sum)
                    + "네 축은 모두 [0,1] 로 정규화되므로 가중치 합이 곧 총점의 상한입니다. "
                    + "합을 1.0 으로 맞추세요 — docs/feed-ranking.md §4.6");
        }
        if (qualityPriorHours <= 0 || freshHalfLifeHours <= 0) {
            throw new IllegalStateException(
                    "quality-prior-hours 와 fresh-half-life-hours 는 0보다 커야 합니다 "
                    + "(0 이면 각각 0으로 나누기와 감쇠 미적용이 됩니다).");
        }
    }

    public double getWeightInterest()     { return weightInterest; }
    public double getWeightRelation()     { return weightRelation; }
    public double getWeightQuality()      { return weightQuality; }
    public double getWeightFreshness()    { return weightFreshness; }
    public double getInterestRef()        { return interestRef; }
    public double getRelationRef()        { return relationRef; }
    public double getQualityRateRef()     { return qualityRateRef; }
    public double getQualityPriorHours()  { return qualityPriorHours; }
    public double getFreshHalfLifeHours() { return freshHalfLifeHours; }

    public void setWeightInterest(double v)     { this.weightInterest = v; }
    public void setWeightRelation(double v)     { this.weightRelation = v; }
    public void setWeightQuality(double v)      { this.weightQuality = v; }
    public void setWeightFreshness(double v)    { this.weightFreshness = v; }
    public void setInterestRef(double v)        { this.interestRef = v; }
    public void setRelationRef(double v)        { this.relationRef = v; }
    public void setQualityRateRef(double v)     { this.qualityRateRef = v; }
    public void setQualityPriorHours(double v)  { this.qualityPriorHours = v; }
    public void setFreshHalfLifeHours(double v) { this.freshHalfLifeHours = v; }
}
