package com.gencode.system.role.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 角色新增/修改请求体
 */
@Data
public class RoleBody {

    /** 修改时必填 */
    private Long id;

    @NotBlank(message = "角色名称不能为空")
    private String name;

    @NotBlank(message = "角色权限字符不能为空")
    private String roleKey;

    private Integer sort;
    private Integer dataScope;
    private Integer status;
    private String remark;
}
