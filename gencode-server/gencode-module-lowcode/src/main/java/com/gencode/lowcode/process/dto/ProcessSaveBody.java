package com.gencode.lowcode.process.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 流程保存请求体（code 不可修改，故不含 code 字段）
 */
@Data
public class ProcessSaveBody {

    /** 流程 ID（修改时必填） */
    @NotNull(message = "流程ID不能为空")
    private Long id;

    /** 流程名称 */
    @NotBlank(message = "流程名称不能为空")
    private String name;

    /** 分类 */
    private String category;

    /** BPMN 2.0 XML */
    private String bpmnXml;

    private String remark;
}
