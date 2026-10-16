package com.knowra.safety.repository;

import com.knowra.safety.entity.TblReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TblReportRepository extends JpaRepository<TblReport, Long> {
    Optional<TblReport> findByReporterSnAndTargetKindAndTargetSn(long reporterSn, String targetKind, long targetSn);
    Page<TblReport> findAllByStatOrderByFrstCrtDtDesc(String stat, Pageable pageable);
    /** 아동 안전 신고를 맨 위로 — 관리자가 가장 먼저 봐야 한다 */
    @org.springframework.data.jpa.repository.Query("SELECT r FROM TblReport r WHERE r.stat = :stat ORDER BY CASE WHEN r.reason = 'CHILD_SAFETY' THEN 0 ELSE 1 END, r.frstCrtDt DESC")
    Page<TblReport> findQueue(@org.springframework.data.repository.query.Param("stat") String stat, Pageable pageable);
    long countByStat(String stat);
    long countByTargetKindAndTargetSnAndStat(String targetKind, long targetSn, String stat);
    long countByTargetKindAndTargetSn(String targetKind, long targetSn);
}
