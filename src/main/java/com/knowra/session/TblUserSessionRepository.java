package com.knowra.session;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface TblUserSessionRepository extends JpaRepository<TblUserSession, String> {
    List<TblUserSession> findAllByUserSnAndLastSeenAtAfterOrderByLastSeenAtDesc(long userSn, LocalDateTime after);
    List<TblUserSession> findAllByUserSn(long userSn);
}
