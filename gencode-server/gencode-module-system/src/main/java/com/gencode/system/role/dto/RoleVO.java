package com.gencode.system.role.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 角色视图对象（详情额外含 menuIds）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleVO extends com.gencode.system.role.entity.SysRole {

    private static final long serialVersionUID = 1L;

    /** 仅详情返回 */
    private List<Long> menuIds;
}
