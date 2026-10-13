package com.knowra.savefolder;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TblPostSaveFolderRepository extends JpaRepository<TblPostSaveFolder, Long> {
    List<TblPostSaveFolder> findAllByUserSnOrderByFrstCrtDtAsc(long userSn);
    long countByUserSn(long userSn);
    boolean existsByUserSnAndName(long userSn, String name);
}
