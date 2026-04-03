package com.knowra.user.repository;

import com.knowra.user.entity.TblUserInterestScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TblUserInterestScoreRepository extends JpaRepository<TblUserInterestScore, Long> {

    Optional<TblUserInterestScore> findByUserSnAndTargetTypeAndTargetSn(
            long userSn, String targetType, long targetSn);

    List<TblUserInterestScore> findAllByUserSnAndTargetType(long userSn, String targetType);
}
