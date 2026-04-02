package com.knowra.post.entity;

import com.knowra.community.entity.TblCommPost;
import com.knowra.user.entity.TblUser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class PostSaveDTO {

    // PostSaveDTO.java에 추가
    public PostSaveDTO(TblPostSave tblPostSave, long userSn, String userId, String authorNm,
                       String commNm, String commDsplNm, String postTtl, String postCntnt, LocalDateTime frstCrtDt,
                       int viewCnt, int likeCnt, int cmtCnt,
                       String myLikeTyp, boolean mySaved, String pfpUrl) {
        this.tblPostSave = tblPostSave;
        this.userSn      = userSn;
        this.userId      = userId;
        this.authorNm    = authorNm;
        this.commNm      = commNm;
        this.commDsplNm  = commDsplNm;
        this.postTtl     = postTtl;
        this.postCntnt   = postCntnt;
        this.frstCrtDt   = frstCrtDt;
        this.viewCnt     = viewCnt;
        this.likeCnt     = likeCnt;
        this.cmtCnt      = cmtCnt;
        this.myLikeTyp   = myLikeTyp;
        this.mySaved     = mySaved;
        this.pfpUrl      = pfpUrl;
        this.tagNms      = List.of();
    }


    private TblPostSave tblPostSave;
    private long userSn;
    private String userId;
    private String authorNm;
    private String commNm;
    private String commDsplNm;
    private String postTtl;
    private String postCntnt;
    private LocalDateTime frstCrtDt;
    private int viewCnt;
    private int likeCnt;
    private int cmtCnt;
    private List<String> tagNms;
    private String myLikeTyp; // UP / DOWN / null(안누름)
    private boolean mySaved; // 저장 여부
    private String pfpUrl;
}

