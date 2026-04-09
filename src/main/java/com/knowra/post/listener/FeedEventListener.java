package com.knowra.post.listener;

import com.knowra.community.entity.QTblCommMbr;
import com.knowra.community.event.CommunityMemberChangedEvent;
import com.knowra.post.event.PostCreatedEvent;
import com.knowra.post.event.PostDeletedEvent;
import com.knowra.post.event.PostReactedEvent;
import com.knowra.post.service.FeedCacheService;
import com.knowra.user.entity.QTblUserFlwr;
import com.knowra.user.event.UserFollowChangedEvent;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FeedEventListener {

    @PersistenceContext
    private EntityManager em;

    private final FeedCacheService feedCacheService;

    // POPULAR 가중치: like × 2, comment × 4
    private static final double WEIGHT_LIKE    = 2.0;
    private static final double WEIGHT_COMMENT = 4.0;

    // ── 게시글 생성 ────────────────────────────────────────────────────────
    @Async
    @EventListener
    @Transactional(readOnly = true)
    public void onPostCreated(PostCreatedEvent event) {
        String member    = event.postKind() + ":" + event.postSn();
        double timestamp = event.createdAt().toEpochSecond(ZoneOffset.UTC);

        // 글로벌 피드 업데이트
        feedCacheService.add(feedCacheService.latestKey(),  member, timestamp);
        feedCacheService.add(feedCacheService.popularKey(), member, 0.0);

        // 작성자 팔로워들의 FOLLOWING 피드에 추가 + PERSONALIZED 무효화
        List<Long> followerSns = fetchFollowers(event.userSn());
        for (Long followerSn : followerSns) {
            String followingKey = feedCacheService.followingKey(followerSn);
            if (feedCacheService.exists(followingKey)) {
                feedCacheService.add(followingKey, member, timestamp);
            }
            feedCacheService.invalidate(feedCacheService.personalizedKey(followerSn));
        }

        // COMM_POST: 해당 커뮤니티 멤버들의 PERSONALIZED도 무효화
        if ("COMM".equals(event.postKind()) && event.commSn() != null) {
            fetchCommMembers(event.commSn()).forEach(memberSn ->
                    feedCacheService.invalidate(feedCacheService.personalizedKey(memberSn)));
        }
    }

    // ── 게시글 삭제 ────────────────────────────────────────────────────────
    @Async
    @EventListener
    public void onPostDeleted(PostDeletedEvent event) {
        String member = event.postKind() + ":" + event.postSn();
        feedCacheService.remove(feedCacheService.popularKey(), member);
        feedCacheService.remove(feedCacheService.latestKey(),  member);
        // per-user FOLLOWING 캐시는 TTL 또는 다음 재조회 시 자연 제거됨
    }

    // ── 좋아요/댓글 → POPULAR 점수 갱신 ──────────────────────────────────
    @Async
    @EventListener
    public void onPostReacted(PostReactedEvent event) {
        if (!feedCacheService.exists(feedCacheService.popularKey())) return;
        String member = event.postKind() + ":" + event.postSn();
        double weight = "LIKE".equals(event.actionType()) ? WEIGHT_LIKE : WEIGHT_COMMENT;
        feedCacheService.incrementScore(feedCacheService.popularKey(), member, weight * event.scoreDelta());
    }

    // ── 팔로우/언팔로우 → 내 FOLLOWING + PERSONALIZED 무효화 ────────────
    @Async
    @EventListener
    public void onFollowChanged(UserFollowChangedEvent event) {
        feedCacheService.invalidateUserFeeds(event.myUserSn());
    }

    // ── 커뮤니티 가입/탈퇴 → 내 PERSONALIZED 무효화 ──────────────────────
    @Async
    @EventListener
    public void onCommunityMemberChanged(CommunityMemberChangedEvent event) {
        feedCacheService.invalidate(feedCacheService.personalizedKey(event.userSn()));
    }

    // ── DB 헬퍼: 팔로워 목록 ─────────────────────────────────────────────
    private List<Long> fetchFollowers(long authorUserSn) {
        QTblUserFlwr qFlwr = QTblUserFlwr.tblUserFlwr;
        return new JPAQueryFactory(em)
                .select(qFlwr.flwrUserSn)
                .from(qFlwr)
                .where(qFlwr.flwngUserSn.eq(authorUserSn).and(qFlwr.actvtnYn.eq("Y")))
                .fetch();
    }

    // ── DB 헬퍼: 커뮤니티 활성 멤버 목록 ─────────────────────────────────
    private List<Long> fetchCommMembers(long commSn) {
        QTblCommMbr qMbr = QTblCommMbr.tblCommMbr;
        return new JPAQueryFactory(em)
                .select(qMbr.userSn)
                .from(qMbr)
                .where(qMbr.commSn.eq(commSn).and(qMbr.stat.eq("ACTIVE")))
                .fetch();
    }
}
