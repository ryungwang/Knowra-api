package com.knowra.common.repository;

import com.knowra.common.entity.TblComFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface TblComFileRepository extends JpaRepository<TblComFile, String> {
    TblComFile findByAtchFileSn(Long atchFileSn);
    List<TblComFile> findAllByPsnTblSn(String psnTblSn);
    TblComFile findByPsnTblSn(String psnTblSn);
    TblComFile findByPsnTblSnAndAtchFilePathNmContaining(String psnTblSn, String pathKeyword);
}
