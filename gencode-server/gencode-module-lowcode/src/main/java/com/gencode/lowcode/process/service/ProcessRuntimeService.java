package com.gencode.lowcode.process.service;

import com.gencode.common.result.PageResult;
import com.gencode.lowcode.process.dto.ProcessCommentBody;
import com.gencode.lowcode.process.dto.ProcessStartBody;
import com.gencode.lowcode.process.dto.ProcessTaskVO;
import com.gencode.lowcode.process.dto.ProcessTransferBody;
import com.gencode.lowcode.process.dto.ProcessTraceVO;
import com.gencode.lowcode.process.entity.LcProcessInstance;

/**
 * 流程运行服务：发起 / 待办 / 已办 / 审批 / 轨迹
 */
public interface ProcessRuntimeService {

    /**
     * 发起流程：启动 Flowable 实例，写 lc_process_instance + 首任务 pending
     *
     * @return lc_process_instance.id
     */
    Long start(String code, ProcessStartBody body);

    /** 我发起的实例分页（含当前节点/状态） */
    PageResult<LcProcessInstance> mineInstances(String status, Integer pageNum, Integer pageSize);

    /** 我的待办（Flowable assignee=当前人） */
    PageResult<ProcessTaskVO> todo(Integer pageNum, Integer pageSize);

    /** 我的已办（HistoryService finished 任务） */
    PageResult<ProcessTaskVO> done(Integer pageNum, Integer pageSize);

    /** 通过：流转到下一节点；流程结束置 instance.status=approved */
    void approve(String taskId, ProcessCommentBody body);

    /** 驳回：终止流程，instance.status=rejected */
    void reject(String taskId, ProcessCommentBody body);

    /** 转办：改 Flowable assignee + 记录 transferred */
    void transfer(String taskId, ProcessTransferBody body);

    /** 撤回：仅发起人且 running 时可撤，deleteProcessInstance 并 status=withdrawn */
    void withdraw(Long instanceId);

    /** 实例详情 + 审批轨迹 */
    ProcessTraceVO trace(Long instanceId);
}
