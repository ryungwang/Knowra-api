package com.knowra.community.repository;

import com.knowra.community.entity.TblComm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TblCommRepository extends JpaRepository<TblComm, Long> {

    TblComm findByCommNm(String commNm);

    TblComm findByCommSn(Long commSn);

    List<TblComm> findAllByCommSnIn(List<Long> commSnList);

    @Query("SELECT c FROM TblComm c LEFT JOIN FETCH c.logoFile LEFT JOIN FETCH c.bnrFile JOIN c.members m WHERE m.userSn = :userSn AND m.stat = 'ACTIVE' AND m.actvtnYn = 'Y'")
    List<TblComm> findAllByMemberUserSn(@Param("userSn") long userSn);
}
