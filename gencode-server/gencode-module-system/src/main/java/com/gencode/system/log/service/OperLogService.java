package com.gencode.system.log.service;

import com.gencode.common.result.PageResult;
import com.gencode.system.log.dto.OperLogQuery;
import com.gencode.system.log.entity.SysOperLog;

/**
 * 操作日志服务
 */
public interface OperLogService {

    PageResult<SysOperLog> page(OperLogQuery query);
}
