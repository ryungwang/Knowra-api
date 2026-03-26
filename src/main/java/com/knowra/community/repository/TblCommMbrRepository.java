package com.knowra.community.repository;

import com.knowra.community.entity.TblCommMbr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TblCommMbrRepository extends JpaRepository<TblCommMbr, Long> {

    List<TblCommMbr> findAllByCommSn(long commSn);

    List<TblCommMbr> findAllByUserSnAndStatAndActvtnYn(long userSn, String stat, String actvtnYn);

    Optional<TblCommMbr> findByCommSnAndUserSn(long commSn, long userSn);
}
