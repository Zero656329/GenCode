package com.gencode.lowcode.form.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 表单新建请求体（新建草稿：schemaJson 置空，version=0，status=0）
 */
@Data
public class FormCreateBody {

    /** 表单编码（全局唯一） */
    @NotBlank(message = "表单编码不能为空")
    private String code;

    /** 表单名称 */
    @NotBlank(message = "表单名称不能为空")
    private String name;

    private String remark;
}
