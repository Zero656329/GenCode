package com.gencode.lowcode.http.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 调用日志分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HttpLogQuery extends PageQuery {

    /** 接口编码（精确过滤） */
    private String apiCode;
}
