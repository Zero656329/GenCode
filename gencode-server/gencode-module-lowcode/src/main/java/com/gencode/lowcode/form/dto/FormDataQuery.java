package com.gencode.lowcode.form.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 填报数据分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FormDataQuery extends PageQuery {

    /** 表单编码（精确过滤） */
    private String formCode;
}
