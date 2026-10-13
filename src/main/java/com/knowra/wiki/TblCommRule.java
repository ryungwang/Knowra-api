package com.knowra.wiki;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TBL_COMM_RULE", schema = "KNOWRA_COMMUNITY")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblCommRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "RULE_SN") private Long ruleSn;
    @Column(name = "COMM_SN", nullable = false) private long commSn;
    @Column(name = "ORD", nullable = false) private int ord;
    @Column(name = "TITLE", nullable = false, length = 60) private String title;
    @Builder.Default @Column(name = "BODY", nullable = false, length = 300) private String body = "";
}
