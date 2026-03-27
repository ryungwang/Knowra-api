package com.knowra.admin.scheduler;

import com.knowra.admin.service.ContentGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentGenerationScheduler {

    private final ContentGenerationService contentGenerationService;

    // 매일 오전 9시 자동 실행
    @Scheduled(cron = "${content.generation.cron:0 0 9 * * *}")
    public void scheduledGenerate() {
        log.info("[ContentGen] 스케줄 실행 시작");
        contentGenerationService.generate();
    }
}
