package com.knowra.event;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface CommEventRepository extends JpaRepository<TblCommEvent, Long> {
    List<TblCommEvent> findAllByCommSnAndStartsAtAfterOrderByStartsAtAsc(long commSn, LocalDateTime after);
    List<TblCommEvent> findTop20ByCommSnAndStartsAtBeforeOrderByStartsAtDesc(long commSn, LocalDateTime before);
    List<TblCommEvent> findAllByStatAndStartsAtBetween(String stat, LocalDateTime from, LocalDateTime to);
}
