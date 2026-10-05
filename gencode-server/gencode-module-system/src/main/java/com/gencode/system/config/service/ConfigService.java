package com.gencode.system.config.service;

import com.gencode.common.result.PageResult;
import com.gencode.system.config.dto.ConfigBody;
import com.gencode.system.config.dto.ConfigQuery;
import com.gencode.system.config.entity.SysConfig;

/**
 * 参数配置服务
 */
public interface ConfigService {

    PageResult<SysConfig> page(ConfigQuery query);

    /** 按键取字符串值 */
    String getValueByKey(String key);

    void create(ConfigBody body);

    void update(ConfigBody body);

    void delete(Long id);
}
