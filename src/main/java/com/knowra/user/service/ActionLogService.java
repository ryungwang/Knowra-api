package com.knowra.user.service;

import com.knowra.user.entity.TblUserActionLog;
import com.knowra.user.repository.TblUserActionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActionLogService {

    private final TblUserActionLogRepository actionLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(long userSn, String targetType, long targetSn, String actionType) {
        try {
            actionLogRepository.save(TblUserActionLog.builder()
                    .userSn(userSn)
                    .targetType(targetType)
                    .targetSn(targetSn)
                    .actionType(actionType)
                    .build());
        } catch (Exception ignored) {
            // 로그 실패가 메인 요청에 영향을 주지 않도록 예외 흡수
        }
    }
}
