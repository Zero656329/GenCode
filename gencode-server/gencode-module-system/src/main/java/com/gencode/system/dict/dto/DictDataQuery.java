package com.gencode.system.dict.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DictDataQuery extends PageQuery {

    /** 按类型过滤 */
    private String dictType;
    /** 匹配 label/value */
    private String keyword;
    private Integer status;
}
