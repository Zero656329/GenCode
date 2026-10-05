package com.gencode.system.log.service;

import com.gencode.common.result.PageResult;
import com.gencode.system.log.dto.LoginLogQuery;
import com.gencode.system.log.entity.SysLoginLog;

/**
 * 登录日志服务
 */
public interface LoginLogService {

    /** 记录登录日志 */
    void record(SysLoginLog loginLog);

    PageResult<SysLoginLog> page(LoginLogQuery query);
}
