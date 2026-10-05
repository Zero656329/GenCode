package com.gencode.lowcode.http.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gencode.lowcode.http.entity.LcHttpLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 第三方 HTTP 调用日志 Mapper（租户条件由租户插件自动追加）
 */
@Mapper
public interface LcHttpLogMapper extends BaseMapper<LcHttpLog> {
}
