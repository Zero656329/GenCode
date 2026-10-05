package com.gencode.system.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录结果：token + 用户信息
 */
@Data
@AllArgsConstructor
public class LoginResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;
    private LoginUser user;
}
