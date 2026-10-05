package com.gencode.lowcode.form.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FormQuery extends PageQuery {

    /** 关键字（匹配 code/name） */
    private String keyword;

    /** 状态：0草稿 1已发布 2停用 */
    private Integer status;
}
