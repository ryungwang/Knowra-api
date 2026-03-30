package com.knowra.community.repository;

import com.knowra.community.entity.TblCommPostTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TblCommPostTagRepository extends JpaRepository<TblCommPostTag, Long> {

    List<TblCommPostTag> findByCommPostSn(long commPostSn);

    void deleteByCommPostSn(long commPostSn);
}
