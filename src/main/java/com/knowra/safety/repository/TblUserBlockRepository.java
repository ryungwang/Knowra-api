package com.knowra.safety.repository;

import com.knowra.safety.entity.TblUserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TblUserBlockRepository extends JpaRepository<TblUserBlock, Long> {
    Optional<TblUserBlock> findByUserSnAndBlockedSn(long userSn, long blockedSn);
    List<TblUserBlock> findAllByUserSn(long userSn);
    boolean existsByUserSnAndBlockedSn(long userSn, long blockedSn);
}
