package com.knowra.user.repository;

import com.knowra.user.entity.TblUserFlwr;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TblUserFlwrRepository extends JpaRepository<TblUserFlwr, Long> {

    Optional<TblUserFlwr> findByFlwrUserSnAndFlwngUserSn(long flwrUserSn, long flwngUserSn);
}
