package com.knowra.post.service;

import com.knowra.cmm.errlog.ErrorLog;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 예약 글을 때가 되면 올린다. 30초마다 보므로 예약 시각보다 최대 30초 늦게 올라간다(화면은 분 단위로만
 * 고르게 한다). 글마다 따로 트랜잭션 — 하나가 실패해도 나머지는 올라간다.
 */
@Component
@RequiredArgsConstructor
public class ScheduledPostPublisher {

    private final PostService postService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void publishDue() {
        for (Long postSn : postService.dueScheduled()) {
            try {
                postService.publishScheduled(postSn);
            } catch (Exception e) {
                ErrorLog.report(e);
            }
        }
    }
}
