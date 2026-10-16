package com.knowra.post.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Table(
        name = "TBL_POST",
        schema = "KNOWRA_POST",
        indexes = {
                @Index(name = "IDX_POST_USER",        columnList = "USER_SN"),
                @Index(name = "IDX_POST_COMM",        columnList = "COMM_SN"),
                @Index(name = "IDX_POST_FRST_CRT_DT", columnList = "FRST_CRT_DT")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TblPost extends BasePostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "POST_SN")
    @Comment("게시글 SN (PK)")
    private Long postSn;

    @Column(name = "POST_TYP", length = 10, nullable = false)
    @Comment("NORMAL / NOTICE")
    @Builder.Default
    private String postTyp = "NORMAL";

    /**
     * 커뮤니티 소속. NULL 이면 전체 공개 글.
     *
     * <p>예전엔 커뮤니티 글이 별도 테이블(tbl_comm_post 계열)이었고 컬럼은 이 하나만 달랐다.
     * 그 차이 때문에 서비스·피드·저장·알림이 전부 두 갈래로 갈라져 있었다 — V2 에서 합쳤다.
     * 커뮤니티 글의 노출 범위(비공개 커뮤니티)는 조회 쪽에서
     * {@code CommunityQueryHelper.accessCondition} 으로 거른다.
     */
    /** 말머리(V31) — 그 커뮤니티 운영진이 정한 분류. 없으면 NULL */
    @Column(name = "FLAIR_SN")
    private Long flairSn;

    /** 연재(V39) — 글쓴이가 묶은 연재. 없으면 NULL */
    @Column(name = "SERIES_SN")
    private Long seriesSn;

    @Column(name = "COMM_SN")
    @Comment("커뮤니티 SN (tbl_comm 논리 참조) — NULL 이면 전체 공개 글")
    private Long commSn;

    /** 인용한 글. 새 글을 쓸 때만 정하고 고칠 때는 바꾸지 않는다. V56. */
    @Column(name = "QUOTE_POST_SN", updatable = false)
    private Long quotePostSn;

    /** 스포일러 글 — 목록·미리보기에서 본문 요약과 사진을 가린다. 글쓴이·운영진이 켜고 끈다. V54. */
    @Column(name = "SPOILER", nullable = false)
    private boolean spoiler;

    /** 커뮤니티 상단 고정 시각. NULL 이면 고정 아님. 커뮤니티 운영진만, 커뮤니티당 최대 3개. */
    @Column(name = "PINNED_AT")
    private java.time.LocalDateTime pinnedAt;

    /** 예약 게시 시각. STAT='SCHEDULED' 인 동안 의미가 있고, 올라간 뒤엔 기록으로 남는다. V20. */
    @Column(name = "RSRV_AT")
    private java.time.LocalDateTime rsrvAt;
}
