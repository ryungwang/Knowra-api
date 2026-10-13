package com.knowra.poll.repository;

import com.knowra.poll.entity.TblPoll;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TblPollRepository extends JpaRepository<TblPoll, Long> {
    Optional<TblPoll> findByPostSn(long postSn);
    List<TblPoll> findAllByPostSnIn(Collection<Long> postSns);
}
