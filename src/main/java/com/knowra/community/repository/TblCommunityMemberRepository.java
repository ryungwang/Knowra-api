package com.knowra.community.repository;

import com.knowra.community.entity.TblCommunityMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TblCommunityMemberRepository extends JpaRepository<TblCommunityMember, Long> {

    List<TblCommunityMember> findAllByCommSn(long commSn);

    List<TblCommunityMember> findAllByUserSnAndStatAndActvtnYn(long userSn, String stat, String actvtnYn);
}
