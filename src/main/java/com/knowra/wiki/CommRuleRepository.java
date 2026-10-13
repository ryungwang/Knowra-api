package com.knowra.wiki;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CommRuleRepository extends JpaRepository<TblCommRule, Long> {
    List<TblCommRule> findAllByCommSnOrderByOrdAsc(long commSn);
    long countByCommSn(long commSn);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TblCommRule r where r.commSn = :commSn")
    int deleteAllOf(@Param("commSn") long commSn);
}
