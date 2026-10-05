package com.gencode.system.log.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gencode.common.result.PageResult;
import com.gencode.framework.operlog.OperLogEvent;
import com.gencode.framework.operlog.OperLogSaver;
import com.gencode.system.log.dto.OperLogQuery;
import com.gencode.system.log.entity.SysOperLog;
import com.gencode.system.log.mapper.SysOperLogMapper;
import com.gencode.system.log.service.OperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务实现：查询 + 框架 OperLogSaver 的异步落库实现
 * （sys_oper_log 在租户插件忽略表中，插入时 tenant_id 已由切面填好，异步线程无需租户上下文）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperLogServiceImpl implements OperLogService, OperLogSaver {

    private final SysOperLogMapper operLogMapper;

    @Override
    public PageResult<SysOperLog> page(OperLogQuery query) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getTitle())) {
            wrapper.like(SysOperLog::getTitle, query.getTitle());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysOperLog::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(query.getOperName())) {
            wrapper.like(SysOperLog::getOperName, query.getOperName());
        }
        if (StrUtil.isNotBlank(query.getBeginTime())) {
            wrapper.apply("oper_time >= {0}", query.getBeginTime() + " 00:00:00");
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            wrapper.apply("oper_time <= {0}", query.getEndTime() + " 23:59:59");
        }
        wrapper.orderByDesc(SysOperLog::getOperTime).orderByDesc(SysOperLog::getId);
        Page<SysOperLog> page = operLogMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Async("gencodeAsyncExecutor")
    @Override
    public void save(OperLogEvent event) {
        try {
            SysOperLog entity = new SysOperLog();
            BeanUtil.copyProperties(event, entity);
            operLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
        }
    }
}
