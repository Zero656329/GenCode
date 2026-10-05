package com.gencode.system.log.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gencode.common.result.PageResult;
import com.gencode.system.log.dto.LoginLogQuery;
import com.gencode.system.log.entity.SysLoginLog;
import com.gencode.system.log.mapper.SysLoginLogMapper;
import com.gencode.system.log.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 登录日志服务实现
 */
@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl implements LoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    @Override
    public void record(SysLoginLog loginLog) {
        loginLogMapper.insert(loginLog);
    }

    @Override
    public PageResult<SysLoginLog> page(LoginLogQuery query) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getUsername())) {
            wrapper.like(SysLoginLog::getUsername, query.getUsername());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysLoginLog::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(query.getIp())) {
            wrapper.like(SysLoginLog::getIp, query.getIp());
        }
        // beginTime/endTime 为 yyyy-MM-dd，转成完整时间范围
        if (StrUtil.isNotBlank(query.getBeginTime())) {
            wrapper.apply("login_time >= {0}", query.getBeginTime() + " 00:00:00");
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            wrapper.apply("login_time <= {0}", query.getEndTime() + " 23:59:59");
        }
        wrapper.orderByDesc(SysLoginLog::getLoginTime).orderByDesc(SysLoginLog::getId);
        Page<SysLoginLog> page = loginLogMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }
}
