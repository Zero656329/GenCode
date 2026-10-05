package com.gencode.lowcode.process.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程新建请求体（新建草稿：bpmnXml 可空，flowKey 空，publishVersion=0，status=0）
 */
@Data
public class ProcessCreateBody {

    /** 流程编码（全局唯一，创建后不可修改） */
    @NotBlank(message = "流程编码不能为空")
    private String code;

    /** 流程名称 */
    @NotBlank(message = "流程名称不能为空")
    private String name;

    /** 分类 */
    private String category;

    /** BPMN 2.0 XML（草稿可空，部署前必须填写） */
    private String bpmnXml;

    private String remark;
}
