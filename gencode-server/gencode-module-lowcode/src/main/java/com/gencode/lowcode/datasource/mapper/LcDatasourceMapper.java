package com.gencode.lowcode.datasource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gencode.lowcode.datasource.entity.LcDatasource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部数据源 Mapper
 */
@Mapper
public interface LcDatasourceMapper extends BaseMapper<LcDatasource> {
}
