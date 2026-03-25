package com.knowra.community.repository;

import com.knowra.community.entity.TblCommunities;
import com.knowra.community.entity.TblCommunityMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TblCommunityMemberRepository extends JpaRepository<TblCommunityMember, String> {

    List<TblCommunityMember> findAllByCommunitySn(long communitySn);
}
