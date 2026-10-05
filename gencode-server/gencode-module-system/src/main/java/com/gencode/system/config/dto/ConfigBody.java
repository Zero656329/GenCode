package com.gencode.system.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 参数新增/修改请求体
 */
@Data
public class ConfigBody {

    /** 修改时必填 */
    private Long id;

    @NotBlank(message = "参数名称不能为空")
    private String configName;

    @NotBlank(message = "参数键不能为空")
    private String configKey;

    private String configValue;
    private Integer status;
    private String remark;
}
