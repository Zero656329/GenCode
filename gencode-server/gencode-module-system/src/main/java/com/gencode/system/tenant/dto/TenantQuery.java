package com.gencode.system.tenant.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantQuery extends PageQuery {

    /** 匹配 name/tenantId */
    private String keyword;
    private Integer status;
}
