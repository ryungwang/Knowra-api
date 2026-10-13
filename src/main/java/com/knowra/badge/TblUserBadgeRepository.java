package com.knowra.badge;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TblUserBadgeRepository extends JpaRepository<TblUserBadge, Long> {
    List<TblUserBadge> findAllByUserSn(long userSn);
}
