package com.knowra.post.repository;

import com.knowra.post.entity.TblPost;
import com.knowra.post.entity.TblPostSave;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TblPostRepository extends JpaRepository<TblPost, Long> {

    TblPost findByPostSn(long postSn);
}
