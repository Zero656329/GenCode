package com.gencode.system.user.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象（含部门名；详情额外含 roleIds）
 */
@Data
public class UserVO {

    private Long id;
    private String tenantId;
    private Long deptId;
    private String deptName;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private Integer userType;
    private Integer status;
    private String loginIp;
    private LocalDateTime loginDate;
    private String remark;
    private LocalDateTime createTime;
    /** 仅详情返回 */
    private List<Long> roleIds;
}
