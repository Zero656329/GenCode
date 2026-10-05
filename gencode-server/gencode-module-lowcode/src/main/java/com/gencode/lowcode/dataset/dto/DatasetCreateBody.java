package com.gencode.lowcode.dataset.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据集新建请求体（sqlText 保存时跑 SQL 安全校验，仅允许单条 SELECT）
 */
@Data
public class DatasetCreateBody {

    /** 数据集编码（全局唯一） */
    @NotBlank(message = "数据集编码不能为空")
    private String code;

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
