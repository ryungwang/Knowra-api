package com.knowra.community.repository;

import com.knowra.community.entity.TblCommPost;
import com.knowra.community.entity.TblCommPostTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TblCommPostTagRepository extends JpaRepository<TblCommPostTag, Long> {

}
