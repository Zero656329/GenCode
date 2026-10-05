package com.gencode.system.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户新增/修改请求体
 */
@Data
public class TenantBody {

    /** 修改时必填 */
    private Long id;

    @NotBlank(message = "租户编号不能为空")
    private String tenantId;

    @NotBlank(message = "租户名称不能为空")
    private String name;

    private String contactPhone;
    private Integer status;
    private LocalDateTime expireTime;
    private String remark;
}
