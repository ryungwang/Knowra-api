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
}
