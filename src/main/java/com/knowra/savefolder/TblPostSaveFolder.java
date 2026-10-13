package com.knowra.savefolder;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_POST_SAVE_FOLDER", schema = "KNOWRA_POST")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblPostSaveFolder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "FOLDER_SN") private Long folderSn;
    @Column(name = "USER_SN", nullable = false) private long userSn;
    @Column(name = "NAME", nullable = false, length = 40) private String name;
    @Builder.Default @Column(name = "FRST_CRT_DT", nullable = false, updatable = false) private LocalDateTime frstCrtDt = LocalDateTime.now();
}
