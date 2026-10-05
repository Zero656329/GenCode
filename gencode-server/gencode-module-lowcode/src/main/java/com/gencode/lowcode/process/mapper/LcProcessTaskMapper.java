package com.gencode.lowcode.process.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gencode.lowcode.process.entity.LcProcessTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程审批任务 Mapper
 */
@Mapper
public interface LcProcessTaskMapper extends BaseMapper<LcProcessTask> {
}
