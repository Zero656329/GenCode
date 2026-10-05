package com.gencode.common.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带租户字段的基础实体（tenant_id 参与租户插件过滤的业务表继承）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantBaseEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户编号（需序列化给前端展示） */
    private String tenantId;
}
