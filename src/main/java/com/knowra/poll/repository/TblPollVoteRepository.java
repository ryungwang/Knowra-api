package com.knowra.poll.repository;

import com.knowra.poll.entity.TblPollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TblPollVoteRepository extends JpaRepository<TblPollVote, Long> {
    List<TblPollVote> findAllByPollSnAndUserSn(long pollSn, long userSn);

    @Modifying(flushAutomatically = true)
    @Query("delete from TblPollVote v where v.pollSn = :pollSn and v.userSn = :userSn")
    int deleteMine(@Param("pollSn") long pollSn, @Param("userSn") long userSn);

    @Query("select count(distinct v.userSn) from TblPollVote v where v.pollSn = :pollSn")
    long countVoters(@Param("pollSn") long pollSn);

    long countByPollSn(long pollSn);

    /**
     * 선택지별 표 수를 다시 센다 — 증감 누적 대신 매번 실제 행으로.
     * clearAutomatically: 네이티브 update 는 이미 읽어 둔 엔티티를 갱신하지 않는다 — 비우지 않으면
     * 같은 트랜잭션에서 돌려주는 결과가 한 박자 늦은 숫자였다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update knowra_post.tbl_poll_option o set vote_cnt = (select count(*) from knowra_post.tbl_poll_vote v where v.option_sn = o.option_sn) where o.poll_sn = :pollSn", nativeQuery = true)
    int recount(@Param("pollSn") long pollSn);
}
