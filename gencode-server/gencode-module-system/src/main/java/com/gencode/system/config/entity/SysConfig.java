package com.gencode.system.config.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统参数配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    private String configName;
    private String configKey;
    private String configValue;
    private Integer status;
    private String remark;
}
