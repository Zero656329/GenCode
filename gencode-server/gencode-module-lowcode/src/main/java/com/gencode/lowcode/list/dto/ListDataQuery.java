package com.gencode.lowcode.list.dto;

import lombok.Data;

import java.util.Map;

/**
 * 列表数据查询请求体
 */
@Data
public class ListDataQuery {

    /** 搜索参数（search 配置的 field -> value） */
    private Map<String, Object> params;

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 排序字段（须在 list_schema.columns 或 pkField 中） */
    private String orderBy;

    /** 排序方向：asc / desc */
    private String orderDir;
}
