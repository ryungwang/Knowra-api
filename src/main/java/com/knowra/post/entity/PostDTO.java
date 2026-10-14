package com.knowra.post.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글 응답 DTO. 전체 공개 글과 커뮤니티 글이 같은 모양이다.
 *
 * <p>{@code commSn} 이 null 이면 전체 공개 글, 아니면 그 커뮤니티의 글이다. 예전의
 * {@code postKind("POST"|"COMM")} 는 두 테이블 시절의 흔적이라 없앴다 — 클라이언트는
 * {@code commSn != null} 로 판단한다.
 */
@Getter
@Setter
public class PostDTO {

    private String postTyp;          // "NOTICE" | "NORMAL"
    private long postSn;

    // ── 커뮤니티 (전체 공개 글이면 전부 null) ──────────────────────
    private Long commSn;
    private String commNm;
    private String commDsplNm;

    // ── 작성자 ──────────────────────────────────────────────────
    private long userSn;
    private String nickName;
    private String authorNm;
    private String pfpUrl;           // atchFilePathNm/strgFileNm.atchFileExtnNm

    // ── 본문·통계 ────────────────────────────────────────────────
    private String postTtl;
    private String postCntnt;
    private LocalDateTime frstCrtDt;
    private int viewCnt;
    private int likeCnt;
    private int cmtCnt;
    private List<String> tagNms;

    // ── 조회자 기준 ──────────────────────────────────────────────
    private String myLikeTyp;        // "UP" | "DOWN" | null
    private boolean mySaved;
    private boolean hasPoll;         // 피드 카드의 「투표」 표시
    private boolean pinned;          // 커뮤니티 상단 고정
    /** 익명 커뮤니티 글 — 작성자 칸은 비우고 「익명」. 신고 처리 등 관리자 화면만 실제 작성자를 본다. */
    private boolean anonymous;
    /** 내가 쓴 글인가 — 익명 글은 화면이 닉네임으로 알 수 없어서 서버가 알려 준다 */
    private boolean mine;

    // ── 피드 근거 ─────────────────────────────────────────────
    // 이 글이 왜 이 자리에 있는지. 점수만 매기고 이유를 숨기면 사용자는 피드를 신뢰할 수 없다.
    // INTEREST | FOLLOWING | JOINED_COMM | POPULAR | FRESH | EXPLORE
    private String feedReason;
    private String feedReasonDetail;   // INTEREST 면 맞은 태그명

    public PostDTO(String postTyp, long postSn, Long commSn, String commNm, String commDsplNm,
                   long userSn, String nickName, String authorNm,
                   String postTtl, String postCntnt, LocalDateTime frstCrtDt,
                   int viewCnt, int likeCnt, int cmtCnt,
                   String myLikeTyp, boolean mySaved) {
        this.postTyp    = postTyp;
        this.postSn     = postSn;
        this.commSn     = commSn;
        this.commNm     = commNm;
        this.commDsplNm = commDsplNm;
        this.userSn     = userSn;
        this.nickName   = nickName;
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
