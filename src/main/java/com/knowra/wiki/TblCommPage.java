package com.knowra.wiki;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_COMM_PAGE", schema = "KNOWRA_COMMUNITY")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblCommPage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "PAGE_SN") private Long pageSn;
    @Column(name = "COMM_SN", nullable = false) private long commSn;
    @Column(name = "SLUG", nullable = false, length = 40) private String slug;
    @Column(name = "TITLE", nullable = false, length = 80) private String title;
    @Builder.Default @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT") private String content = "";
    @Builder.Default @Column(name = "ORD", nullable = false) private int ord = 0;
    @Column(name = "UPDATED_BY", nullable = false) private long updatedBy;
    @Builder.Default @Column(name = "UPDATED_AT", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
}
