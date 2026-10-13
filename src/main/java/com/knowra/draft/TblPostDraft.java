package com.knowra.draft;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_POST_DRAFT", schema = "KNOWRA_POST")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblPostDraft {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "DRAFT_SN") private Long draftSn;
    @Column(name = "USER_SN", nullable = false) private long userSn;
    @Column(name = "COMM_SN") private Long commSn;
    @Builder.Default @Column(name = "TITLE", nullable = false, length = 300) private String title = "";
    @Builder.Default @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT") private String content = "";
    @Builder.Default @Column(name = "TAGS", nullable = false, length = 1000) private String tags = "";
    @Builder.Default @Column(name = "UPDATED_AT", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
}
