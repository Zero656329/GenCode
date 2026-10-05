package com.gencode.system.log.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志分页查询（beginTime/endTime 格式 yyyy-MM-dd）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperLogQuery extends PageQuery {

    private String title;
    private Integer status;
    private String operName;
    private String beginTime;
    private String endTime;
}
