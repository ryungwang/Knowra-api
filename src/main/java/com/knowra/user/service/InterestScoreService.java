package com.knowra.user.service;

import com.knowra.user.entity.TblUserActionLog;
import com.knowra.user.entity.TblUserInterestScore;
import com.knowra.user.repository.TblUserInterestScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterestScoreService {

    private final TblUserInterestScoreRepository interestScoreRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void update(long userSn, String targetType, long targetSn, double delta) {
        try {
            upsert(userSn, targetType, targetSn, delta);
        } catch (Exception ignored) {}
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateForTags(long userSn, List<Long> tagSns, double delta) {
        try {
            for (Long tagSn : tagSns) {
                upsert(userSn, TblUserActionLog.TARGET_TAG, tagSn, delta);
            }
        } catch (Exception ignored) {}
    }

    private void upsert(long userSn, String targetType, long targetSn, double delta) {
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
    }
}
