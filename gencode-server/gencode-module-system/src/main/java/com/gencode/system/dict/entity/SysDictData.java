package com.gencode.system.dict.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    private String dictType;
    private String label;
    private String value;
    private Integer sort;
    private Integer isDefault;
    private Integer status;
    private String remark;
}
