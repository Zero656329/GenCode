package com.gencode.system.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户（租户忽略表：全平台可见）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class SysTenant extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String name;
    private String contactPhone;
    private Integer status;
    /** 过期时间（空=永久） */
    private LocalDateTime expireTime;
    private String remark;
}
