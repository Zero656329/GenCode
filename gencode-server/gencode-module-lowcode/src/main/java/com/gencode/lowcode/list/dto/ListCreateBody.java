package com.gencode.lowcode.list.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 列表新建请求体
 */
@Data
public class ListCreateBody {

    /** 列表编码（全局唯一） */
    @NotBlank(message = "列表编码不能为空")
    private String code;

    /** 列表名称 */
    @NotBlank(message = "列表名称不能为空")
    private String name;

    /** 数据源类型：TABLE | SQL | API */
    @NotBlank(message = "数据源类型不能为空")
    private String sourceType;

    /** 数据源配置 JSON 字符串 */
    private String sourceConfig;

    /** 列表 Schema JSON 字符串（columns/search/buttons） */
    private String listSchema;

    private String remark;
}
