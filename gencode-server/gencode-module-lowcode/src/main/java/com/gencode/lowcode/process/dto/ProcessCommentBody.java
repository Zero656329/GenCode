package com.gencode.lowcode.process.dto;

import lombok.Data;

/**
 * 审批意见请求体（通过/驳回共用：{ comment }）
 */
@Data
public class ProcessCommentBody {

    /** 审批意见 */
    private String comment;
}
