package com.knowra.push.repository;

import com.knowra.push.entity.TblPushSub;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TblPushSubRepository extends JpaRepository<TblPushSub, Long> {
    Optional<TblPushSub> findByEndpoint(String endpoint);
    List<TblPushSub> findAllByUserSn(long userSn);
    long countByUserSn(long userSn);
}
