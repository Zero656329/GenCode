package com.gencode.lowcode.http.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 接口定义分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HttpApiQuery extends PageQuery {

    /** 关键字（匹配 code/name） */
    private String keyword;
}
