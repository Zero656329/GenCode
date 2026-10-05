package com.gencode.system.dept.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门（树形）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    /** 父部门 ID，0=根 */
    private Long parentId;
    /** 祖级列表，逗号分隔（如 0,100） */
    private String ancestors;
    private String name;
    private Integer orderNo;
    private String leader;
    private String phone;
    private String email;
    private Integer status;
}
