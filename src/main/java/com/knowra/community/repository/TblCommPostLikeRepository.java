package com.knowra.community.repository;

import com.knowra.community.entity.TblCommPostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TblCommPostLikeRepository extends JpaRepository<TblCommPostLike, Long> {

    TblCommPostLike findByCommPostSnAndUserSn(long commPostSn, long userSn);

    List<TblCommPostLike> findByCommPostSnInAndUserSn(List<Long> commPostSns, long userSn);
}
