package com.knowra.post.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class CmtDTO {

    private long cmtSn;
    private long userSn;
    private String nickName;
    private String authorNm;
    private String cmtCntnt;
    private int likeCnt;
    private LocalDateTime frstCrtDt;
    private List<CmtDTO> replies;

    // 반응 수 ({"LIKE": 3, "LOVE": 1, ...})
    private Map<String, Long> reactions;
    // 내 반응 타입 (LIKE / LOVE / HAHA / WOW / SAD / ANGRY / null)
    private String myReactTyp;
    private String pfpUrl;

    /** ACTIVE | DELETED — 답글이 달린 댓글은 지워도 자리(DELETED)가 남는다. */
    private String stat;
    /** 수정 시각. 생성 시각과 다르면 화면이 "수정됨"을 붙인다. */
    private LocalDateTime mdfcnDt;

    /** 댓글 사진(없으면 null). 지워진 댓글은 사진도 내보내지 않는다. */
    private String imgUrl;

    /** 익명 커뮤니티 댓글 — 작성자 칸은 별칭(글쓴이·익명N)이고 userSn 은 0. 생성자 밖에서 채운다. */
    private boolean anonymous;
    /** 내가 쓴 댓글인가 — 익명이면 화면이 닉네임으로 알 수 없어서 서버가 알려 준다 */
    private boolean mine;
    /** 내가 숨긴 사람의 댓글 — 화면이 접어 둔다(차단과 달리 내용은 온다) */
    private boolean muted;
    /** 맨 위에 고정된 댓글(글쓴이·운영진이 고른 것) */
    private boolean pinned;
    /** 남아 있는 수정 기록 수 — 0 보다 크면 「수정됨」을 눌러 이전 내용을 본다 */
    private int editCount;
}
