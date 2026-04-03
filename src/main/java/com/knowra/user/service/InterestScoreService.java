package com.knowra.user.service;

import com.knowra.user.entity.TblUserInterestScore;
import com.knowra.user.repository.TblUserInterestScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterestScoreService {

    private final TblUserInterestScoreRepository interestScoreRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void update(long userSn, String targetType, long targetSn, double delta) {
        try {
            TblUserInterestScore record = interestScoreRepository
                    .findByUserSnAndTargetTypeAndTargetSn(userSn, targetType, targetSn)
                    .orElse(TblUserInterestScore.builder()
                            .userSn(userSn)
                            .targetType(targetType)
                            .targetSn(targetSn)
                            .build());

            double newScore = Math.max(0, record.getScore() + delta);
            record.setScore(newScore);
            interestScoreRepository.save(record);
        } catch (Exception ignored) {
            // 스코어 실패가 메인 요청에 영향을 주지 않도록 예외 흡수
        }
    }
}
