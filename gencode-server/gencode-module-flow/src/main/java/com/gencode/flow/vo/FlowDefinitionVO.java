package com.gencode.flow.vo;

import lombok.Data;

import java.util.Date;

/**
 * 流程定义视图对象
 */
@Data
public class FlowDefinitionVO {

    private String id;
    private String key;
    private String name;
    private Integer version;
    private String deploymentId;
    /** 部署时间 */
    private Date deployTime;
}
