package com.gencode.system.config.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 参数分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConfigQuery extends PageQuery {

    /** 匹配 configName/configKey */
    private String keyword;
    private Integer status;
}
