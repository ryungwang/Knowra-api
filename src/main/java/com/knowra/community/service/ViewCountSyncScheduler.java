package com.knowra.community.service;

import com.knowra.cmm.service.RedisApiService;
import com.knowra.community.repository.TblCommPostRepository;
import com.knowra.post.repository.TblPostRepository;
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
    private final TblPostRepository tblPostRepository;

    private static final int REDIS_DB = 15;

    @Scheduled(fixedDelay = 600000) // 10분마다
    @Transactional
    public void syncViewCounts() {
        Set<String> keys = redisApiService.getViewCountKeys(REDIS_DB);
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            long delta = redisApiService.getAndDeleteViewCount(REDIS_DB, key);
            if (delta == 0) continue;

            // key = "{type}:viewcnt:{postSn}"
            String[] parts = key.split(":viewcnt:");
            if (parts.length != 2) continue;
            String type = parts[0];
            long postSn = Long.parseLong(parts[1]);

            if ("comm".equals(type)) {
                tblCommPostRepository.findById(postSn).ifPresent(post -> {
                    post.setViewCnt(post.getViewCnt() + (int) delta);
                    tblCommPostRepository.save(post);
                });
            } else if ("post".equals(type)) {
                tblPostRepository.findById(postSn).ifPresent(post -> {
                    post.setViewCnt(post.getViewCnt() + (int) delta);
                    tblPostRepository.save(post);
                });
            }
        }
    }
}
