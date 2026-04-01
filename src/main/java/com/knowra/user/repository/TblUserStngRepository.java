package com.knowra.user.repository;

import com.knowra.user.entity.TblUser;
import com.knowra.user.entity.TblUserStng;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
@Repository
public interface TblUserStngRepository extends JpaRepository<TblUserStng, Long> {

}
