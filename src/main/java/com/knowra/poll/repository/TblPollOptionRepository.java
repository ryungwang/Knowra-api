package com.knowra.poll.repository;

import com.knowra.poll.entity.TblPollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TblPollOptionRepository extends JpaRepository<TblPollOption, Long> {
    List<TblPollOption> findAllByPollSnOrderByOrdAsc(long pollSn);
}
