package com.gencode.system.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gencode.common.entity.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    private Long deptId;
    private String username;
    private String nickname;
    /** BCrypt 密文，不对外序列化 */
    @JsonIgnore
    private String password;
    private String phone;
    private String email;
    private String avatar;
    /** 用户类型（0超管 1普通） */
    private Integer userType;
    /** 状态（0正常 1停用） */
    private Integer status;
    private String loginIp;
    private LocalDateTime loginDate;
    private String remark;
}
