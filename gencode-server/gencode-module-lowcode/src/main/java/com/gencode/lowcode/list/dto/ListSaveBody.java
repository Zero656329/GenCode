package com.gencode.lowcode.list.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 列表保存请求体（code 不可修改，故不含 code 字段）
 */
@Data
public class ListSaveBody {

    /** 列表 ID（修改时必填） */
    @NotNull(message = "列表ID不能为空")
    private Long id;

    /** 列表名称 */
    @NotBlank(message = "列表名称不能为空")
    private String name;

    /** 数据源类型：TABLE | SQL | API */
    private String sourceType;

    /** 数据源配置 JSON 字符串 */
    private String sourceConfig;

    /** 列表 Schema JSON 字符串（columns/search/buttons） */
    private String listSchema;

    private String remark;
}
