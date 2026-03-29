package com.knowra.post.repository;

import com.knowra.post.entity.TblPostSave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TblPostSaveRepository extends JpaRepository<TblPostSave, Long> {

    TblPostSave findByUserSnAndPostSnAndPostKind(long userSn, long postSn, String postKind);
}
