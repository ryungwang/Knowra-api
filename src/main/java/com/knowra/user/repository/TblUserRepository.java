package com.knowra.user.repository;

import com.knowra.user.entity.TblUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Transactional
@Repository
public interface TblUserRepository extends JpaRepository<TblUser, Long> {

    TblUser findByUserSn(Long userSn);

    Optional<TblUser> findByEmail(String email);

    Optional<TblUser> findByPhone(String phone);

    Optional<TblUser> findByLoginId(String loginId);

    Optional<TblUser> findByNickName(String nickName);

    /** 소셜 계정 매칭 — 제공자 + 제공자 안의 ID. */
    Optional<TblUser> findBySnsNameAndSnsId(String snsName, String snsId);

    @Modifying
    @Query("UPDATE TblUser U SET U.lgnFailNmtm = :lgnFailNmtm WHERE U.userSn = :userSn")
    void setLgnFailNmtmUpd(@Param("userSn") long userSn, @Param("lgnFailNmtm") long lgnFailNmtm);

    @Modifying
    @Query("UPDATE TblUser U SET U.status = 'C' WHERE U.userSn = :userSn")
    void setSuspensionOfUseUpd(@Param("userSn") long userSn);

    // ── 관리자 콘솔 ──
    long countByStatus(String status);
    long countByFrstCrtDtAfter(LocalDateTime since);

    /** 닉네임·아이디·이메일 부분 일치. q 가 비면 전체(최근 가입 순). */
    @Query("select u from TblUser u where (:q = '' or lower(u.nickName) like lower(concat('%', :q, '%'))"
            + " or lower(u.loginId) like lower(concat('%', :q, '%')) or lower(coalesce(u.email, '')) like lower(concat('%', :q, '%')))"
            + " order by u.userSn desc")
    Page<TblUser> searchForAdmin(@Param("q") String q, Pageable pageable);
}
