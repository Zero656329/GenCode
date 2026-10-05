package com.gencode.system.dept.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 部门新增/修改请求体（ancestors 由后端维护；契约中的 remark 字段表结构暂无，忽略存储）
 */
@Data
public class DeptBody {

    /** 修改时必填 */
    private Long id;

    /** 父部门 ID，0=根 */
    private Long parentId;

    @NotBlank(message = "部门名称不能为空")
    private String name;

    private Integer orderNo;
    private String leader;
    private String phone;
    private String email;
    private Integer status;
    /** 契约字段：sys_dept 暂无该列，仅做入参兼容 */
    private String remark;
}
