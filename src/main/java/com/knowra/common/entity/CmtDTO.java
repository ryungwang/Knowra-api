package com.knowra.common.entity;

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
    private String authorNm;
    private String cmtCntnt;
    private int likeCnt;
    private LocalDateTime frstCrtDt;
    private List<CmtDTO> replies;

    // 반응 수 ({"LIKE": 3, "LOVE": 1, ...})
    private Map<String, Long> reactions;
    // 내 반응 타입 (LIKE / LOVE / HAHA / WOW / SAD / ANGRY / null)
    private String myReactTyp;
}
