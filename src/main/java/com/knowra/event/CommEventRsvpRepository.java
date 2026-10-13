package com.knowra.event;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommEventRsvpRepository extends JpaRepository<TblCommEventRsvp, Long> {
    List<TblCommEventRsvp> findAllByEventSnOrderByFrstCrtDtAsc(long eventSn);
    List<TblCommEventRsvp> findAllByEventSnIn(Collection<Long> eventSns);
    Optional<TblCommEventRsvp> findByEventSnAndUserSn(long eventSn, long userSn);
    long countByEventSn(long eventSn);
}
