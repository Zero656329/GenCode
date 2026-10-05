package com.gencode.system.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/修改请求体（password 可空=不修改，roleIds 可空=不动）
 */
@Data
public class UserBody {

    /** 修改时必填 */
    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "昵称不能为空")
    private String nickname;

    private String password;
    private Long deptId;
    private String phone;
    private String email;
    private Integer status;
    private String remark;
    private List<Long> roleIds;
}
