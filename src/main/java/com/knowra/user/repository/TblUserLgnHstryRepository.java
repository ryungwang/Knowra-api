package com.knowra.user.repository.user;

import com.knowra.user.entity.user.TblUserLgnHstry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TblUserLgnHstryRepository extends JpaRepository<TblUserLgnHstry, String> {

    @Query(value = "SELECT * FROM TBL_USER_LGN_HSTRY WHERE USER_SN = :userSn ORDER BY LGN_DT DESC LIMIT 1", nativeQuery = true)
    TblUserLgnHstry findLatestLoginByUserSn(@Param("userSn") long userSn);
}
