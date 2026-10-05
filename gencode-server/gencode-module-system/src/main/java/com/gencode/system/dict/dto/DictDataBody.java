package com.gencode.system.dict.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典数据新增/修改请求体
 */
@Data
public class DictDataBody {

    /** 修改时必填 */
    private Long id;

    @NotBlank(message = "字典类型不能为空")
    private String dictType;

    @NotBlank(message = "标签不能为空")
    private String label;

    @NotBlank(message = "键值不能为空")
    private String value;

    private Integer sort;
    private Integer isDefault;
    private Integer status;
    private String remark;
}
