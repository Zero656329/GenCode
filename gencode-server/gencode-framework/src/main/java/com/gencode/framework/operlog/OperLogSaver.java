package com.gencode.framework.operlog;

/**
 * 操作日志落库接口：framework 只定义契约，由 system 模块提供异步实现
 */
public interface OperLogSaver {

    void save(OperLogEvent event);
}
