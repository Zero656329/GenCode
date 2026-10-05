package com.gencode.lowcode.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 低代码流程实例（发起时创建，与 Flowable 流程实例通过 flowInstanceId 关联）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_process_instance")
public class LcProcessInstance extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    /** lc_process_def.id */
    private Long processDefId;
    /** Flowable 流程实例ID */
    private String flowInstanceId;
    /** 业务键 */
    private String businessKey;
    /** 关联表单编码 */
    private String formCode;
    /** 关联表单数据ID */
    private Long formDataId;
    /** 流程标题 */
    private String title;
    /** 当前节点（流程结束后为空） */
    private String currentNode;
    /** 状态：running审批中 approved已通过 rejected已驳回 withdrawn已撤回 */
    private String status;
    /** 发起人账号 */
    private String startUser;
}
