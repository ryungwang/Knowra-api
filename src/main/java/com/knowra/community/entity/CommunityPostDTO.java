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
    private String authorNm;
    private String postTyp;
    private String postTtl;
    private LocalDateTime frstCrtDt;
    private int viewCnt;
    private int likeCnt;
    private int cmtCnt;
    private List<String> tagNms;
}