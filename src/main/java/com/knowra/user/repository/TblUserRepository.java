package com.knowra.user.repository;

import com.knowra.user.entity.TblUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
@Repository
public interface TblUserRepository extends JpaRepository<TblUser, Long> {

    TblUser findByUserSn(Long userSn);

    Optional<TblUser> findByEmail(String email);

    Optional<TblUser> findByLoginId(String loginId);

    @Modifying
    @Query("UPDATE TblUser U SET U.lgnFailNmtm = :lgnFailNmtm WHERE U.userSn = :userSn")
    void setLgnFailNmtmUpd(@Param("userSn") long userSn, @Param("lgnFailNmtm") long lgnFailNmtm);

    @Modifying
    @Query("UPDATE TblUser U SET U.status = 'C' WHERE U.userSn = :userSn")
    void setSuspensionOfUseUpd(@Param("userSn") long userSn);
}
