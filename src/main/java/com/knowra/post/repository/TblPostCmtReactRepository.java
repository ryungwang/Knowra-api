package com.knowra.post.repository;

import com.knowra.community.entity.TblCommPostCmtReact;
import com.knowra.post.entity.TblPost;
import com.knowra.post.entity.TblPostCmt;
import com.knowra.post.entity.TblPostCmtReact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TblPostCmtReactRepository extends JpaRepository<TblPostCmtReact, Long> {

    TblPostCmtReact findByPostCmtSnAndUserSn(long postCmtSn, long userSn);

    // 댓글 목록의 반응 수 일괄 조회 (N+1 방지)
    @Query("SELECT r.postCmtSn, r.reactTyp, COUNT(r) FROM TblPostCmtReact r " +
            "WHERE r.postCmtSn IN :cmtSns GROUP BY r.postCmtSn, r.reactTyp")
    List<Object[]> countByCmtSns(@Param("cmtSns") List<Long> cmtSns);

    // 특정 유저의 반응 목록 일괄 조회
    List<TblPostCmtReact> findByPostCmtSnInAndUserSn(List<Long> cmtSns, long userSn);
}
