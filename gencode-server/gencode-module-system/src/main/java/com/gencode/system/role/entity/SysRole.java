package com.gencode.system.role.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    private String name;
    /** 角色权限字符串（如 super_admin） */
    private String roleKey;
    private Integer sort;
    /** 数据范围（1全部 2本部门及以下 3本部门 4仅本人） */
    private Integer dataScope;
    private Integer status;
    private String remark;
}
