package com.knowra.user.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginDTO {

    private String loginType;
    private Long userSn;
    private String loginId;
    private String password;
}
