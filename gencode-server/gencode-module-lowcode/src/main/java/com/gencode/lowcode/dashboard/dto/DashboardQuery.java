package com.gencode.lowcode.dashboard.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 大屏分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DashboardQuery extends PageQuery {

    /** 关键字（匹配 code/name） */
    private String keyword;

    /** 状态：0草稿 1已发布 2停用 */
    private Integer status;
}
