package com.gencode.system.dict.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DictTypeQuery extends PageQuery {

    /** 匹配 name/dictType */
    private String keyword;
    private Integer status;
}
