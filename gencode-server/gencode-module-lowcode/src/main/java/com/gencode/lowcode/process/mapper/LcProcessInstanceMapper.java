package com.gencode.lowcode.process.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gencode.lowcode.process.entity.LcProcessInstance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程实例 Mapper
 */
@Mapper
public interface LcProcessInstanceMapper extends BaseMapper<LcProcessInstance> {
}
