package com.knowra.community.service;

import com.knowra.cmm.service.RedisApiService;
import com.knowra.community.repository.TblCommPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class ViewCountSyncScheduler {

    private final RedisApiService redisApiService;
    private final TblCommPostRepository tblCommPostRepository;

    private static final int REDIS_DB = 15;

    @Scheduled(fixedDelay = 600000) // 10분마다
    @Transactional
    public void syncViewCounts() {
        Set<String> keys = redisApiService.getViewCountKeys(REDIS_DB);
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            long delta = redisApiService.getAndDeleteViewCount(REDIS_DB, key);
            if (delta == 0) continue;

            // key = "post:viewcnt:{commPostSn}" — 커뮤니티 게시글 조회수 동기화
            long commPostSn = Long.parseLong(key.replace("post:viewcnt:", ""));
            tblCommPostRepository.findById(commPostSn).ifPresent(post -> {
                post.setViewCnt(post.getViewCnt() + (int) delta);
                tblCommPostRepository.save(post);
            });
        }

        // TODO: 일반 게시글 조회수 동기화
        //       일반 게시글은 별도 프리픽스 "gen:post:viewcnt:{postSn}" 를 사용하도록 RedisApiService에 메서드 추가 후
        //       TblPostRepository를 주입받아 동일한 방식으로 처리 예정
    }
}
