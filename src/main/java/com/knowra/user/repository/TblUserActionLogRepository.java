package com.knowra.user.repository;

import com.knowra.user.entity.TblUserActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TblUserActionLogRepository extends JpaRepository<TblUserActionLog, Long> {
}
