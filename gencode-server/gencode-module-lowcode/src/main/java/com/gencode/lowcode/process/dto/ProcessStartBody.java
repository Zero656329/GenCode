package com.gencode.lowcode.process.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 流程发起请求体：{ title, formCode?, formDataId?, vars: { approver, approver2? } }
 */
@Data
public class ProcessStartBody {

    /** 流程标题 */
    @NotBlank(message = "流程标题不能为空")
    private String title;

    /** 关联表单编码（可选） */
    private String formCode;

    /** 关联表单数据ID（可选，非空时作为 Flowable businessKey） */
    private Long formDataId;

    /** 流程变量（至少含 approver，两级审批再加 approver2） */
    private Map<String, Object> vars;
}
