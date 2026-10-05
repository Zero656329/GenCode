package com.gencode.lowcode.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 低代码流程定义
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_process_def")
public class LcProcessDef extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程编码（唯一，不可修改） */
    private String code;
    /** 流程名称 */
    private String name;
    /** 分类 */
    private String category;
    /** BPMN 2.0 XML */
    private String bpmnXml;
    /** Flowable processDefinitionKey（部署时从 BPMN process id 解析） */
    private String flowKey;
    /** 部署版本（每部署一次 +1） */
    private Integer publishVersion;
    /** 状态：0草稿 1已发布 2停用 */
    private Integer status;
    private String remark;
}
