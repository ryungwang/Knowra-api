package com.knowra.post.repository;

import com.knowra.post.entity.TblPostCmt;
import com.knowra.post.entity.TblPostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TblPostCmtRepository extends JpaRepository<TblPostCmt, Long> {

}
