package com.knowra.community.repository;

import com.knowra.community.entity.TblCommPostCmtReact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface TblCommPostCmtReactRepository extends JpaRepository<TblCommPostCmtReact, Long> {

    TblCommPostCmtReact findByCommPostCmtSnAndUserSn(long commPostCmtSn, long userSn);

    // 댓글 목록의 반응 수 일괄 조회 (N+1 방지)
    @Query("SELECT r.commPostCmtSn, r.reactTyp, COUNT(r) FROM TblCommPostCmtReact r " +
           "WHERE r.commPostCmtSn IN :cmtSns GROUP BY r.commPostCmtSn, r.reactTyp")
    List<Object[]> countByCmtSns(@Param("cmtSns") List<Long> cmtSns);

    // 특정 유저의 반응 목록 일괄 조회
    List<TblCommPostCmtReact> findByCommPostCmtSnInAndUserSn(List<Long> cmtSns, long userSn);
}
