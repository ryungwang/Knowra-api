package com.knowra.community.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class CommunityPostDTO {

    private long commPostSn;
    private long commSn;
    private long userSn;
    private String userId;
    private String authorNm;
    private String postTyp;
    private String postTtl;
    private String postCntnt;
    private LocalDateTime frstCrtDt;
    private int viewCnt;
    private int likeCnt;
    private int cmtCnt;
    private List<String> tagNms;
    private String myLikeTyp; // UP / DOWN / null(안누름)
    private boolean mySaved; // 저장 여부
}