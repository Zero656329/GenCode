package com.gencode.system.auth.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录用户信息（前端全局存储）
 */
@Data
public class LoginUser {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String tenantId;
    private Long deptId;
    private String deptName;
    /** 角色 key 数组 */
    private List<String> roles;
    /** 权限标识数组（超级管理员为 ["*:*:*"]） */
    private List<String> perms;
}
