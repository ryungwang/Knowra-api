package com.knowra.user.repository;

import com.knowra.user.entity.TblUserNotif;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TblUserNotifRepository extends JpaRepository<TblUserNotif, Long> {

    List<TblUserNotif> findTop50ByUserSnOrderByFrstCrtDtDesc(Long userSn);

    long countByUserSnAndIsRead(Long userSn, String isRead);

    @Modifying
    @Query("UPDATE TblUserNotif n SET n.isRead = 'Y' WHERE n.notifSn = :notifSn AND n.userSn = :userSn")
    void markRead(@Param("notifSn") Long notifSn, @Param("userSn") Long userSn);

    @Modifying
    @Query("UPDATE TblUserNotif n SET n.isRead = 'Y' WHERE n.userSn = :userSn AND n.isRead = 'N'")
    void markAllRead(@Param("userSn") Long userSn);
}
