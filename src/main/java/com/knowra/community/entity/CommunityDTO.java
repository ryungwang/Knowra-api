package com.knowra.community.entity;

import com.knowra.common.entity.TblComFile;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CommunityDTO {

    TblCommunities tblCommunities;
    Long totalUser;
    TblComFile logoFile;
    TblComFile bannerFile;
}
