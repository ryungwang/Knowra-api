package com.knowra.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "TBL_USER_INTEREST_SCORE",
    catalog = "KNOWRA_USER",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_INTEREST_USER_TARGET",
        columnNames = {"USER_SN", "TARGET_TYPE", "TARGET_SN"}
    )
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TblUserInterestScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SCORE_SN")
    @Comment("관심도 점수 SN (PK)")
    private Long scoreSn;

    @Column(name = "USER_SN", nullable = false)
    @Comment("유저 SN")
    private long userSn;

    @Column(name = "TARGET_TYPE", length = 20, nullable = false)
    @Comment("COMM | COMM_POST | POST | USER")
    private String targetType;

    @Column(name = "TARGET_SN", nullable = false)
    @Comment("대상 SN")
    private long targetSn;

    @Setter
    @Column(name = "SCORE", nullable = false)
    @Comment("누적 관심도 점수 (0 이상)")
    @Builder.Default
    private double score = 0;

    @Column(name = "UPDT_DT", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    @Comment("최종 업데이트 일시")
    @Builder.Default
    private LocalDateTime updtDt = LocalDateTime.now();
}
