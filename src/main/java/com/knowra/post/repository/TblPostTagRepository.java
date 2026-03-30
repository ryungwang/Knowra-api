package com.knowra.post.repository;

import com.knowra.post.entity.TblPostLike;
import com.knowra.post.entity.TblPostTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TblPostTagRepository extends JpaRepository<TblPostTag, Long> {

    List<TblPostTag> findByPostSn(long postSn);

    void deleteByPostSn(long postSn);
}
