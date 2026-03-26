package com.knowra.common.repository;

import com.knowra.common.entity.TblComFile;
import com.knowra.common.entity.TblTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface TblTagRepository extends JpaRepository<TblTag, String> {
    TblTag findByTagNm(String tagNm);
}
