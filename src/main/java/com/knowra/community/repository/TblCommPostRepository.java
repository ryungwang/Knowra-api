package com.knowra.community.repository;

import com.knowra.community.entity.TblCommPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TblCommPostRepository extends JpaRepository<TblCommPost, Long> {

    TblCommPost findByCommPostSn(Long commPostSn);
}
