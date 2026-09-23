package com.knowra.post.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CommPost / Post 통합 응답 DTO
 * <p>
 * postKind: "COMM" | "POST" 로 출처를 구분한다.<br>
 * COMM: commSn, commNm, commDsplNm 사용<br>
 * POST: commSn·commNm·commDsplNm = null
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
    private String nickName;
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
    private String pfpUrl;           // atchFilePathNm/strgFileNm.atchFileExtnNm

    // ── 피드 근거 ─────────────────────────────────────────────
    // 이 글이 왜 이 자리에 있는지. 점수만 매기고 이유를 숨기면 사용자는 피드를 신뢰할 수 없다.
    // INTEREST | FOLLOWING | JOINED_COMM | POPULAR | FRESH | EXPLORE
    private String feedReason;
    private String feedReasonDetail;   // INTEREST 면 맞은 태그명

    // ── COMM 전용 ──────────────────────────────────────────────
    private Long commSn;
    private String commNm;
    private String commDsplNm;

    // ── QueryDSL Projections.constructor — POST용 ──────────────
    public PostDTO(String postKind, String postTyp, long postSn, long userSn, String nickName, String authorNm,
                   String postTtl, String postCntnt,
                   LocalDateTime frstCrtDt, int viewCnt, int likeCnt, int cmtCnt,
                   String myLikeTyp, boolean mySaved) {
        this.postKind  = postKind;
        this.postTyp   = postTyp;
        this.postSn    = postSn;
        this.userSn    = userSn;
        this.nickName    = nickName;
        this.authorNm  = authorNm;
        this.postTtl   = postTtl;
        this.postCntnt = postCntnt;
        this.frstCrtDt = frstCrtDt;
        this.viewCnt   = viewCnt;
        this.likeCnt   = likeCnt;
        this.cmtCnt    = cmtCnt;
        this.myLikeTyp = myLikeTyp;
        this.mySaved   = mySaved;
        this.tagNms    = List.of();
    }

    // ── QueryDSL Projections.constructor — COMMPOST용 ──────────
    public PostDTO(String postKind, String postTyp, long commSn, String commNm, String commDsplNm, long postSn,
                   long userSn, String nickName, String authorNm,
                   String postTtl, String postCntnt, LocalDateTime frstCrtDt,
                   int viewCnt, int likeCnt, int cmtCnt, String myLikeTyp, boolean mySaved) {
        this.postKind   = postKind;
        this.postTyp    = postTyp;
        this.commSn     = commSn;
        this.commNm     = commNm;
        this.commDsplNm = commDsplNm;
        this.postSn     = postSn;
        this.userSn     = userSn;
        this.nickName     = nickName;
        this.authorNm   = authorNm;
        this.postTtl    = postTtl;
        this.postCntnt  = postCntnt;
        this.frstCrtDt  = frstCrtDt;
        this.viewCnt    = viewCnt;
        this.likeCnt    = likeCnt;
        this.cmtCnt     = cmtCnt;
        this.myLikeTyp  = myLikeTyp;
        this.mySaved    = mySaved;
        this.tagNms     = List.of();
    }
}
