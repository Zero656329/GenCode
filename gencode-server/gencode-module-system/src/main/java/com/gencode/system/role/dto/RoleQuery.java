package com.gencode.system.role.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleQuery extends PageQuery {

    /** 匹配 name/roleKey */
    private String keyword;
    private Integer status;
}
