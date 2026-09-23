package com.knowra.post.listener;

import com.knowra.community.entity.QTblCommMbr;
import com.knowra.community.event.CommunityMemberChangedEvent;
import com.knowra.post.event.PostCreatedEvent;
import com.knowra.post.event.PostDeletedEvent;
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

import java.util.List;

/**
 * 피드 캐시 무효화.
 *
 * <p>캐시를 부분 수정하지 않는다. ZSET 의 score 는 {@code FeedService} 의 랭킹 점수이고,
 * 여기서 알 수 있는 값(타임스탬프·반응 가중치)은 그 척도가 아니기 때문이다.
 *
 * <p><b>좋아요·댓글은 일부러 처리하지 않는다.</b> {@code PostReactedEvent} 는 발행되지만
 * 구독하지 않는다. 반응 한 건이 총점에 주는 영향은 글의 나이와 현재 점수에 따라 달라지고
 * (품질 = {@code saturate(반응량 / (경과시간 + 4))}), 매번 무효화하기에는 반응이 너무
 * 잦다. 전역 피드의 TTL 이 그 역할을 한다.
 */
@Component
@RequiredArgsConstructor
public class FeedEventListener {

    @PersistenceContext
    private EntityManager em;

    private final FeedCacheService feedCacheService;

    // ── 게시글 생성 ────────────────────────────────────────────────────────
    // 캐시를 부분 수정하지 않고 통째로 버린다. ZSET 의 score 는 FeedService 의 랭킹
    // 점수여야 하는데, 여기서 넣을 수 있는 값(타임스탬프·0)은 그 척도가 아니다.
    // 새 글은 다음 조회가 전체를 다시 계산하면서 제자리에 들어간다.
    @Async
    @EventListener
    @Transactional(readOnly = true)
    public void onPostCreated(PostCreatedEvent event) {
        feedCacheService.invalidateGlobalFeeds();

        // 작성자의 팔로워들은 FOLLOWING·PERSONALIZED 둘 다 다시 계산해야 한다.
        fetchFollowers(event.userSn())
                .forEach(feedCacheService::invalidateUserFeeds);

        // COMM_POST: 해당 커뮤니티 멤버들의 PERSONALIZED도 무효화
        if ("COMM".equals(event.postKind()) && event.commSn() != null) {
            fetchCommMembers(event.commSn())
                    .forEach(feedCacheService::invalidatePersonalized);
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
        feedCacheService.invalidatePersonalized(event.userSn());
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
