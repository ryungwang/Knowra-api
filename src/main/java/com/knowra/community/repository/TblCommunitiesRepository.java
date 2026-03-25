package com.knowra.community.repository;

import com.knowra.community.entity.TblCommunities;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TblCommunitiesRepository extends JpaRepository<TblCommunities, Long> {

    TblCommunities findByCommNm(String commNm);

    TblCommunities findByCommSn(Long commSn);

    List<TblCommunities> findAllByCommSnIn(List<Long> commSnList);

    @Query("SELECT c FROM TblCommunities c LEFT JOIN FETCH c.logoFile LEFT JOIN FETCH c.bnrFile JOIN c.members m WHERE m.userSn = :userSn AND m.stat = 'ACTIVE' AND m.actvtnYn = 'Y'")
    List<TblCommunities> findAllByMemberUserSn(@Param("userSn") long userSn);
}
