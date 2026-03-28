package com.knowra.user.repository;

import com.knowra.user.entity.TblUsrFlwr;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TblUsrFlwrRepository extends JpaRepository<TblUsrFlwr, Long> {

    Optional<TblUsrFlwr> findByFlwrUserSnAndFlwngUserSn(long flwrUserSn, long flwngUserSn);
}
