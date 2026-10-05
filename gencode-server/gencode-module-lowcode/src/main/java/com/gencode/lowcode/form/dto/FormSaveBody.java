package com.gencode.lowcode.form.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 表单保存设计请求体（code 不可修改，故不含 code 字段）
 */
@Data
public class FormSaveBody {

    /** 表单 ID（修改时必填） */
    @NotNull(message = "表单ID不能为空")
    private Long id;

    /** 表单名称 */
    @NotBlank(message = "表单名称不能为空")
    private String name;

    /** 设计 Schema JSON（设计器画布整体 JSON 字符串） */
    private String schemaJson;

    private String remark;
}
