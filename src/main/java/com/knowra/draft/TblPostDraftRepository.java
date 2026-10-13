package com.knowra.draft;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TblPostDraftRepository extends JpaRepository<TblPostDraft, Long> {
    List<TblPostDraft> findAllByUserSnOrderByUpdatedAtDesc(long userSn);
    long countByUserSn(long userSn);
}
