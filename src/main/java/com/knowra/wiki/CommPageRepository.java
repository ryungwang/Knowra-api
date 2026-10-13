package com.knowra.wiki;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CommPageRepository extends JpaRepository<TblCommPage, Long> {
    List<TblCommPage> findAllByCommSnOrderByOrdAscPageSnAsc(long commSn);
    Optional<TblCommPage> findByCommSnAndSlug(long commSn, String slug);
    long countByCommSn(long commSn);
}
