package com.gencode.lowcode.datasource.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DatasourceQuery extends PageQuery {

    /** 关键字（匹配 name） */
    private String keyword;
}
