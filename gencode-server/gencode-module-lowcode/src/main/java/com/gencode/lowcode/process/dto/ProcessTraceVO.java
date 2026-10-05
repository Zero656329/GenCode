package com.gencode.lowcode.process.dto;

import com.gencode.lowcode.process.entity.LcProcessInstance;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实例详情 + 审批轨迹：{ instance, tasks }
 */
@Data
public class ProcessTraceVO {

    /** 流程实例 */
    private LcProcessInstance instance;

    /** 轨迹（按 create_time、handle_time 排序） */
    private List<TaskTrace> tasks;

    /**
     * 单条轨迹记录
     */
    @Data
    public static class TaskTrace {

        private Long id;
        /** Flowable 任务ID */
        private String taskId;
        /** 节点名称 */
        private String nodeName;
        /** 办理人账号 */
        private String assignee;
        /** 结果：pending/approved/rejected/transferred */
        private String result;
        /** 审批意见 */
        private String comment;
        /** 办理时间 */
        private LocalDateTime handleTime;
        /** 到达该节点时间 */
        private LocalDateTime createTime;
    }
}
