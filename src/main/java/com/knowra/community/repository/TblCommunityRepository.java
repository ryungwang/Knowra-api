package com.knowra.community.repository;

import com.knowra.community.entity.TblCommunity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TblCommunityRepository extends JpaRepository<TblCommunity, String> {

}
