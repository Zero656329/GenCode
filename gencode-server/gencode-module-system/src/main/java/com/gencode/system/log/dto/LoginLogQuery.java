package com.gencode.system.log.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登录日志分页查询（beginTime/endTime 格式 yyyy-MM-dd）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginLogQuery extends PageQuery {

    private String username;
    private Integer status;
    private String ip;
    private String beginTime;
    private String endTime;
}
