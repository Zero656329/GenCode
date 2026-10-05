package com.gencode.lowcode.process.dto;

import lombok.Data;

/**
 * 运行时已发布流程定义（供发起页使用）
 */
@Data
public class ProcessPublishedVO {

    private String code;
    private String name;
    /** Flowable processDefinitionKey */
    private String flowKey;
    private Integer publishVersion;
}
