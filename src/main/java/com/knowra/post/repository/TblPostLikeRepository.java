package com.knowra.post.repository;

import com.knowra.post.entity.TblPostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TblPostLikeRepository extends JpaRepository<TblPostLike, Long> {

    TblPostLike findByPostSnAndUserSn(long postSn, long userSn);

    List<TblPostLike> findByPostSnInAndUserSn(List<Long> postSns, long userSn);
}
