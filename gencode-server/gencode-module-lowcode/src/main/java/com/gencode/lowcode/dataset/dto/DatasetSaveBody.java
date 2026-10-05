package com.gencode.lowcode.dataset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据集保存请求体（code 不可修改，故不含 code 字段）
 */
@Data
public class DatasetSaveBody {

    /** 数据集 ID（修改时必填） */
    @NotNull(message = "数据集ID不能为空")
    private Long id;

    /** 数据集名称 */
    @NotBlank(message = "数据集名称不能为空")
    private String name;

    /** 查询 SQL（仅 SELECT，支持 #{param} 参数化） */
    @NotBlank(message = "查询 SQL 不能为空")
    private String sqlText;

    /** 参数定义 JSON [{name,label,type,required,defaultValue}] */
    private String paramsJson;

    private String remark;
}
