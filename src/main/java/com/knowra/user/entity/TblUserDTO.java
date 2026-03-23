package com.knowra.user.entity;

import com.knowra.user.entity.user.TblUser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TblUserDTO {

  TblUser tblUser;
  String status;
}
