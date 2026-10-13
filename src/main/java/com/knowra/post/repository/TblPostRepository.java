package com.knowra.post.repository;

import com.knowra.post.entity.TblPost;
import com.knowra.post.entity.TblPostSave;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TblPostRepository extends JpaRepository<TblPost, Long> {

    TblPost findByPostSn(long postSn);

    // ── 관리자 콘솔 ──
    long countByActvtnYn(String actvtnYn);
    long countByActvtnYnAndFrstCrtDtAfter(String actvtnYn, LocalDateTime since);
}
