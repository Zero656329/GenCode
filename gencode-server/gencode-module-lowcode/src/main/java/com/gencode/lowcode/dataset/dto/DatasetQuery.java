package com.gencode.lowcode.dataset.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据集分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DatasetQuery extends PageQuery {

    /** 关键字（匹配 code/name） */
    private String keyword;
}
