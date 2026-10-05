package com.gencode.lowcode.dataset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 低代码数据集定义（参数化 SELECT 取数）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_dataset")
public class LcDataset extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    /** 数据集编码（唯一，不可修改） */
    private String code;
    /** 数据集名称 */
    private String name;
    /** 查询 SQL（仅 SELECT，支持 #{param} 参数化） */
    private String sqlText;
    /** 参数定义 JSON [{name,label,type,required,defaultValue}] */
    private String paramsJson;
    private String remark;
}
