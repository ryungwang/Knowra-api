package com.knowra.safety.repository;

import com.knowra.safety.entity.TblReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TblReportRepository extends JpaRepository<TblReport, Long> {
    Optional<TblReport> findByReporterSnAndTargetKindAndTargetSn(long reporterSn, String targetKind, long targetSn);
    Page<TblReport> findAllByStatOrderByFrstCrtDtDesc(String stat, Pageable pageable);
    long countByStat(String stat);
    long countByTargetKindAndTargetSnAndStat(String targetKind, long targetSn, String stat);
}
