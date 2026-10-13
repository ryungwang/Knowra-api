package com.knowra.safety.repository;

import com.knowra.safety.entity.TblPostHide;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TblPostHideRepository extends JpaRepository<TblPostHide, Long> {
    Optional<TblPostHide> findByUserSnAndPostSn(long userSn, long postSn);
    List<TblPostHide> findAllByUserSnOrderByFrstCrtDtDesc(long userSn);
}
