package com.knowra.common.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

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
}
