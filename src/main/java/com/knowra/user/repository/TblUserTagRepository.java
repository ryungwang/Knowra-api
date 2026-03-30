package com.knowra.user.repository;

import com.knowra.user.entity.TblUser;
import com.knowra.user.entity.TblUserFlwr;
import com.knowra.user.entity.TblUserTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TblUserTagRepository extends JpaRepository<TblUserTag, Long> {

    TblUserTag findByUserSnAndTagSn(long userSn, long tagSn);
}
