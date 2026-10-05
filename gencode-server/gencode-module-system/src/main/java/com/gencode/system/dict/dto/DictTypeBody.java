package com.gencode.system.dict.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典类型新增/修改请求体
 */
@Data
public class DictTypeBody {

    /** 修改时必填 */
    private Long id;

    @NotBlank(message = "字典名称不能为空")
    private String name;

    @NotBlank(message = "字典类型不能为空")
    private String dictType;

    private Integer status;
    private String remark;
}
