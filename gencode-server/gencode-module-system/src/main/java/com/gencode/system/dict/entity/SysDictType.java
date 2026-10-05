package com.gencode.system.dict.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SysDictType extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    private String name;
    /** 字典类型（唯一键） */
    private String dictType;
    private Integer status;
    private String remark;
}
