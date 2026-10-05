package com.gencode.lowcode.process.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 低代码流程审批任务（轨迹表：只追加不删除，表无 update_by/update_time/deleted，
 * 因此不继承 TenantBaseEntity/BaseEntity，不使用 @TableLogic）
 */
@Data
@TableName("lc_process_task")
public class LcProcessTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编号（由租户插件自动填充过滤） */
    private String tenantId;

    /** lc_process_instance.id */
    private Long instanceId;

    /** Flowable 任务ID（同一节点的转办行共用） */
    private String taskId;

    /** 节点名称 */
    private String nodeName;

    /** 办理人账号 */
    private String assignee;

    /** 结果：pending待审批 approved通过 rejected驳回 transferred转办 */
    private String result;

    /** 审批意见 */
    private String comment;

    /** 办理时间 */
    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
