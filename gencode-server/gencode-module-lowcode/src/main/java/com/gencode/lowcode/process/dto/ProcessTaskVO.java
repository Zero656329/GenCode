package com.gencode.lowcode.process.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办/已办列表项（todo 取 Flowable 运行时任务，done 取历史任务）
 */
@Data
public class ProcessTaskVO {

    /** Flowable 任务ID */
    private String taskId;
    /** lc_process_instance.id */
    private Long instanceId;
    /** 流程标题 */
    private String title;
    /** 节点名称 */
    private String nodeName;
    /** 办理人账号 */
    private String assignee;
    /** 发起人账号 */
    private String startUser;
    /** 任务创建时间 */
    private LocalDateTime createTime;
    /** 任务办结时间（已办才有） */
    private LocalDateTime finishTime;
}
