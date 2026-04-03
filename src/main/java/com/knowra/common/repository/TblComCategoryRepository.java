package com.knowra.common.repository;

import com.knowra.common.entity.TblComCategory;
import com.knowra.common.entity.TblComFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface TblComCategoryRepository extends JpaRepository<TblComCategory, String> {
    List<TblComCategory> findAllByActvtnYn(String actvtnYn);
}
