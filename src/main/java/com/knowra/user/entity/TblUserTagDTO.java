package com.knowra.user.entity;

import com.knowra.user.entity.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TblUserTagDTO {

    long tagSn;
    String tagNm;
    long userTagSn;
    long useCount;
}
