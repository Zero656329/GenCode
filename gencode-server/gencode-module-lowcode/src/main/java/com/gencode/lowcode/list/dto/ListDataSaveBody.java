package com.gencode.lowcode.list.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 列表数据行保存请求体（仅 TABLE 型）
 */
@Data
public class ListDataSaveBody {

    /** 保存模式：add 新增 / edit 编辑 */
    @NotBlank(message = "保存模式不能为空")
    private String mode;

    /** 主键值（edit 模式必填） */
    private String pk;

    /** 行数据（field -> value，按 list_schema.columns 白名单过滤） */
    private Map<String, Object> row;
}
