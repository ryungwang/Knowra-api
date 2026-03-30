package com.knowra.post.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CommPost / Post 통합 응답 DTO
 * <p>
 * postTyp: "COMMPOST" | "POST" 로 출처를 구분한다.<br>
 * COMMPOST: commPostSn, commSn, postKind 사용 / commNm·commDsplNm·tblPostSave = null<br>
 * POST:     postSn 사용, commNm·commDsplNm 사용 / commSn·postKind = null
 * </p>
 */
@Getter
@Setter
public class PostDTO {

    // ── 공통 ──────────────────────────────────────────────────
    private String postKind;         // "COMM" | "POST"
    private String postTyp;          // "NOTICE" | "NORMAL"
    private long postSn;             // commPostSn 또는 postSn
    private long userSn;
    private String userId;
    private String authorNm;
    private String postTtl;
    private String postCntnt;
    private LocalDateTime frstCrtDt;
    private int viewCnt;
    private int likeCnt;
    private int cmtCnt;
    private List<String> tagNms;
    private String myLikeTyp;        // "UP" | "DOWN" | null
    private boolean mySaved;

    // ── COMM(커뮤니티게시글) 전용 ──────────────────────────────────────
    private Long commSn;
    private String commNm;           // 출처 커뮤니티 이름
    private String commDsplNm;       // 출처 커뮤니티 표시명

    // ── QueryDSL Projections.constructor — POST(저장글)용 ──────
    public PostDTO(String postKind, String postTyp, long postSn, long userSn, String userId, String authorNm,
                   String postTtl, String postCntnt,
                   LocalDateTime frstCrtDt, int viewCnt, int likeCnt, int cmtCnt,
                   String myLikeTyp, boolean mySaved) {
        this.postKind      = postKind;
        this.postTyp      = postTyp;
        this.postSn       = postSn;
        this.userSn       = userSn;
        this.userId       = userId;
        this.authorNm     = authorNm;
        this.postTtl      = postTtl;
        this.postCntnt    = postCntnt;
        this.frstCrtDt    = frstCrtDt;
        this.viewCnt      = viewCnt;
        this.likeCnt      = likeCnt;
        this.cmtCnt       = cmtCnt;
        this.myLikeTyp    = myLikeTyp;
        this.mySaved      = mySaved;
        this.tagNms       = List.of();
    }

    // ── QueryDSL Projections.constructor — COMMPOST용 ──────────
    public PostDTO(String postKind, String postTyp, long commSn, String commNm, String commDsplNm, long postSn,
                   long userSn, String userId, String authorNm,
                   String postTtl, String postCntnt, LocalDateTime frstCrtDt,
                   int viewCnt, int likeCnt, int cmtCnt, String myLikeTyp, boolean mySaved) {
        this.postKind      = postKind;
        this.postTyp    = postTyp;
        this.postSn     = postSn;
        this.commSn     = commSn;
        this.commNm     = commNm;
        this.commDsplNm = commDsplNm;
        this.userSn     = userSn;
        this.userId     = userId;
        this.authorNm   = authorNm;
        this.postTtl    = postTtl;
        this.postCntnt  = postCntnt;
        this.frstCrtDt  = frstCrtDt;
        this.viewCnt    = viewCnt;
        this.likeCnt    = likeCnt;
        this.cmtCnt     = cmtCnt;
        this.myLikeTyp    = myLikeTyp;
        this.mySaved      = mySaved;
        this.tagNms     = List.of();
    }
}
