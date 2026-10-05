package com.gencode.lowcode.form.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 低代码表单填报数据
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_form_data")
public class LcFormData extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    /** 表单编码 */
    private String formCode;
    /** 填报数据 JSON */
    private String dataJson;
}
