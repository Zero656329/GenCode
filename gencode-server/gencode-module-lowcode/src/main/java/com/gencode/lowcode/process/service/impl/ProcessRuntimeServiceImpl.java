package com.gencode.lowcode.process.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.lowcode.process.dto.ProcessCommentBody;
import com.gencode.lowcode.process.dto.ProcessStartBody;
import com.gencode.lowcode.process.dto.ProcessTaskVO;
import com.gencode.lowcode.process.dto.ProcessTraceVO;
import com.gencode.lowcode.process.dto.ProcessTransferBody;
import com.gencode.lowcode.process.entity.LcProcessDef;
import com.gencode.lowcode.process.entity.LcProcessInstance;
import com.gencode.lowcode.process.entity.LcProcessTask;
import com.gencode.lowcode.process.mapper.LcProcessDefMapper;
import com.gencode.lowcode.process.mapper.LcProcessInstanceMapper;
import com.gencode.lowcode.process.mapper.LcProcessTaskMapper;
import com.gencode.lowcode.process.service.ProcessRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流程运行服务实现：lc_process_instance/lc_process_task 与 Flowable 运行时状态同步。
 * 当前登录人取 Sa-Token 会话中的 username（登录时 session.set("username", ...)），
 * 保证与 sys_user.username 一致，Flowable assignee 与 lc_process_task.assignee 均存 username。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRuntimeServiceImpl implements ProcessRuntimeService {

    /** 实例状态：审批中 */
    private static final String STATUS_RUNNING = "running";
    /** 实例状态：已通过 */
    private static final String STATUS_APPROVED = "approved";
    /** 实例状态：已驳回 */
    private static final String STATUS_REJECTED = "rejected";
    /** 实例状态：已撤回 */
    private static final String STATUS_WITHDRAWN = "withdrawn";

    /** 任务结果：待审批 */
    private static final String RESULT_PENDING = "pending";
    /** 任务结果：通过 */
    private static final String RESULT_APPROVED = "approved";
    /** 任务结果：驳回 */
    private static final String RESULT_REJECTED = "rejected";
    /** 任务结果：转办 */
    private static final String RESULT_TRANSFERRED = "transferred";

    /** 定义状态：已发布（已部署） */
    private static final int DEF_STATUS_PUBLISHED = 1;

    private final LcProcessDefMapper defMapper;
    private final LcProcessInstanceMapper instanceMapper;
    private final LcProcessTaskMapper taskMapper;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long start(String code, ProcessStartBody body) {
        LcProcessDef def = defMapper.selectOne(new LambdaQueryWrapper<LcProcessDef>()
                .eq(LcProcessDef::getCode, code));
        if (def == null) {
            throw new BizException("流程不存在");
        }
        if (def.getStatus() == null || def.getStatus() != DEF_STATUS_PUBLISHED || StrUtil.isBlank(def.getFlowKey())) {
            throw new BizException("流程未部署，无法发起");
        }
        if (body.getVars() == null || body.getVars().isEmpty()) {
            throw new BizException("流程变量 vars 不能为空，且至少包含 approver");
        }
        Object approver = body.getVars().get("approver");
        if (approver == null || StrUtil.isBlank(approver.toString())) {
            throw new BizException("流程变量 approver 不能为空");
        }
        String user = currentUser();
        String businessKey = body.getFormDataId() == null ? null : String.valueOf(body.getFormDataId());

        ProcessInstance flowInstance = runtimeService.startProcessInstanceByKey(
                def.getFlowKey(), businessKey, new HashMap<>(body.getVars()));

        // 首个运行时任务（串行审批模板只有一个；同步执行，start 返回时已创建）
        List<Task> firstTasks = taskService.createTaskQuery()
                .processInstanceId(flowInstance.getId())
                .orderByTaskCreateTime().asc()
                .list();

        LcProcessInstance instance = new LcProcessInstance();
        instance.setProcessDefId(def.getId());
        instance.setFlowInstanceId(flowInstance.getId());
        instance.setBusinessKey(businessKey);
        instance.setFormCode(body.getFormCode());
        instance.setFormDataId(body.getFormDataId());
        instance.setTitle(body.getTitle());
        instance.setCurrentNode(firstTasks.isEmpty() ? null : firstTasks.get(0).getName());
        // 流程定义首节点即结束（无等待任务）时视为已结束
        instance.setStatus(firstTasks.isEmpty() ? STATUS_APPROVED : STATUS_RUNNING);
        instance.setStartUser(user);
        instanceMapper.insert(instance);

        if (!firstTasks.isEmpty()) {
            insertPendingTask(instance.getId(), firstTasks.get(0), approver.toString());
        }
        return instance.getId();
    }

    @Override
    public PageResult<LcProcessInstance> mineInstances(String status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<LcProcessInstance> wrapper = new LambdaQueryWrapper<LcProcessInstance>()
                .eq(LcProcessInstance::getStartUser, currentUser())
                .orderByDesc(LcProcessInstance::getCreateTime);
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(LcProcessInstance::getStatus, status);
        }
        return PageResult.of(instanceMapper.selectPage(toPage(pageNum, pageSize), wrapper));
    }

    @Override
    public PageResult<ProcessTaskVO> todo(Integer pageNum, Integer pageSize) {
        String user = currentUser();
        TaskQuery query = taskService.createTaskQuery().taskAssignee(user);
        long total = query.count();
        if (total == 0) {
            return PageResult.of(List.of(), 0);
        }
        int offset = Math.max(pageNum, 1) - 1;
        int size = Math.max(pageSize, 1);
        List<Task> tasks = query.orderByTaskCreateTime().desc()
                .listPage(offset * size, size);

        Map<String, LcProcessInstance> instanceMap = loadInstancesByFlowIds(
                tasks.stream().map(Task::getProcessInstanceId).toList());
        List<ProcessTaskVO> vos = tasks.stream().map(task -> {
            ProcessTaskVO vo = new ProcessTaskVO();
            vo.setTaskId(task.getId());
            vo.setNodeName(task.getName());
            vo.setAssignee(task.getAssignee());
            vo.setCreateTime(toLocalDateTime(task.getCreateTime()));
            LcProcessInstance instance = instanceMap.get(task.getProcessInstanceId());
            if (instance != null) {
                vo.setInstanceId(instance.getId());
                vo.setTitle(instance.getTitle());
                vo.setStartUser(instance.getStartUser());
            }
            return vo;
        }).toList();
        return PageResult.of(vos, total);
    }

    @Override
    public PageResult<ProcessTaskVO> done(Integer pageNum, Integer pageSize) {
        String user = currentUser();
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(user)
                .finished();
        long total = query.count();
        if (total == 0) {
            return PageResult.of(List.of(), 0);
        }
        int offset = Math.max(pageNum, 1) - 1;
        int size = Math.max(pageSize, 1);
        List<HistoricTaskInstance> tasks = query.orderByHistoricTaskInstanceEndTime().desc()
                .listPage(offset * size, size);

        Map<String, LcProcessInstance> instanceMap = loadInstancesByFlowIds(
                tasks.stream().map(HistoricTaskInstance::getProcessInstanceId).toList());
        List<ProcessTaskVO> vos = tasks.stream().map(task -> {
            ProcessTaskVO vo = new ProcessTaskVO();
            vo.setTaskId(task.getId());
            vo.setNodeName(task.getName());
            vo.setAssignee(task.getAssignee());
            vo.setCreateTime(toLocalDateTime(task.getCreateTime()));
            vo.setFinishTime(toLocalDateTime(task.getEndTime()));
            LcProcessInstance instance = instanceMap.get(task.getProcessInstanceId());
            if (instance != null) {
                vo.setInstanceId(instance.getId());
                vo.setTitle(instance.getTitle());
                vo.setStartUser(instance.getStartUser());
            }
            return vo;
        }).toList();
        return PageResult.of(vos, total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String taskId, ProcessCommentBody body) {
        Task task = requireRuntimeTask(taskId);
        LcProcessInstance instance = requireInstanceByFlowId(task.getProcessInstanceId());
        String user = currentUser();
        String comment = StrUtil.nullToEmpty(body == null ? null : body.getComment());

        // 意见随 complete 写入流程变量（下一节点可读取），并落 lc_process_task 轨迹
        Map<String, Object> vars = new HashMap<>();
        vars.put("approveComment", comment);
        taskService.claim(taskId, user);
        taskService.complete(taskId, vars);

        completeTaskRow(taskId, RESULT_APPROVED, comment);

        // 流转后该流程实例的运行时任务：有则推进当前节点并新增 pending 行；无则流程结束
        List<Task> nextTasks = taskService.createTaskQuery()
                .processInstanceId(task.getProcessInstanceId())
                .orderByTaskCreateTime().asc()
                .list();
        if (nextTasks.isEmpty()) {
            updateInstanceStatus(instance.getId(), STATUS_APPROVED);
        } else {
            for (Task next : nextTasks) {
                insertPendingTask(instance.getId(), next,
                        StrUtil.blankToDefault(next.getAssignee(), ""));
            }
            instanceMapper.update(null, new LambdaUpdateWrapper<LcProcessInstance>()
                    .eq(LcProcessInstance::getId, instance.getId())
                    .set(LcProcessInstance::getCurrentNode, nextTasks.get(0).getName()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String taskId, ProcessCommentBody body) {
        Task task = requireRuntimeTask(taskId);
        LcProcessInstance instance = requireInstanceByFlowId(task.getProcessInstanceId());
        String comment = StrUtil.nullToEmpty(body == null ? null : body.getComment());

        runtimeService.deleteProcessInstance(task.getProcessInstanceId(), RESULT_REJECTED + ":" + comment);
        completeTaskRow(taskId, RESULT_REJECTED, comment);
        updateInstanceStatus(instance.getId(), STATUS_REJECTED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(String taskId, ProcessTransferBody body) {
        Task task = requireRuntimeTask(taskId);
        LcProcessInstance instance = requireInstanceByFlowId(task.getProcessInstanceId());
        String comment = StrUtil.nullToEmpty(body.getComment());

        taskService.setAssignee(taskId, body.getAssignee());

        // 原任务行置 transferred，新增一行 pending 指向新办理人（同一 Flowable 任务ID）
        completeTaskRow(taskId, RESULT_TRANSFERRED, comment);
        LcProcessTask row = new LcProcessTask();
        row.setInstanceId(instance.getId());
        row.setTaskId(taskId);
        row.setNodeName(task.getName());
        row.setAssignee(body.getAssignee());
        row.setResult(RESULT_PENDING);
        taskMapper.insert(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long instanceId) {
        LcProcessInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException("流程实例不存在");
        }
        if (!STATUS_RUNNING.equals(instance.getStatus())) {
            throw new BizException("仅审批中的流程可撤回");
        }
        if (!StrUtil.equals(instance.getStartUser(), currentUser())) {
            throw new BizException("仅发起人可撤回");
        }
        if (StrUtil.isNotBlank(instance.getFlowInstanceId())) {
            runtimeService.deleteProcessInstance(instance.getFlowInstanceId(), STATUS_WITHDRAWN);
        }
        // 当前 pending 任务行以"发起人撤回"结案
        taskMapper.update(null, new LambdaUpdateWrapper<LcProcessTask>()
                .eq(LcProcessTask::getInstanceId, instanceId)
                .eq(LcProcessTask::getResult, RESULT_PENDING)
                .set(LcProcessTask::getResult, RESULT_REJECTED)
                .set(LcProcessTask::getComment, "发起人撤回")
                .set(LcProcessTask::getHandleTime, LocalDateTime.now()));
        updateInstanceStatus(instanceId, STATUS_WITHDRAWN);
    }

    @Override
    public ProcessTraceVO trace(Long instanceId) {
        LcProcessInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException("流程实例不存在");
        }
        List<LcProcessTask> rows = taskMapper.selectList(new LambdaQueryWrapper<LcProcessTask>()
                .eq(LcProcessTask::getInstanceId, instanceId)
                .orderByAsc(LcProcessTask::getCreateTime)
                .orderByAsc(LcProcessTask::getHandleTime)
                .orderByAsc(LcProcessTask::getId));
        List<ProcessTraceVO.TaskTrace> tasks = rows.stream().map(row -> {
            ProcessTraceVO.TaskTrace trace = new ProcessTraceVO.TaskTrace();
            trace.setId(row.getId());
            trace.setTaskId(row.getTaskId());
            trace.setNodeName(row.getNodeName());
            trace.setAssignee(row.getAssignee());
            trace.setResult(row.getResult());
            trace.setComment(row.getComment());
            trace.setHandleTime(row.getHandleTime());
            trace.setCreateTime(row.getCreateTime());
            return trace;
        }).collect(Collectors.toList());

        ProcessTraceVO vo = new ProcessTraceVO();
        vo.setInstance(instance);
        vo.setTasks(tasks);
        return vo;
    }

    // ------------------------------------------------------------------ 私有方法

    /**
     * 当前登录人账号：登录时 StpUtil.login(user.id)，username 存于会话
     * （MyMetaObjectHandler 同款取法），兜底 getLoginIdAsString。
     */
    private String currentUser() {
        try {
            Object username = StpUtil.getSession().get("username");
            if (username != null && StrUtil.isNotBlank(username.toString())) {
                return username.toString();
            }
            return StpUtil.getLoginIdAsString();
        } catch (Exception e) {
            throw new BizException("未获取到当前登录人");
        }
    }

    private Page<LcProcessInstance> toPage(Integer pageNum, Integer pageSize) {
        return new Page<>(
                pageNum == null ? 1 : Math.max(pageNum, 1),
                pageSize == null ? 10 : Math.max(pageSize, 1));
    }

    private Task requireRuntimeTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException("任务不存在或已办理");
        }
        return task;
    }

    private LcProcessInstance requireInstanceByFlowId(String flowInstanceId) {
        LcProcessInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<LcProcessInstance>()
                .eq(LcProcessInstance::getFlowInstanceId, flowInstanceId)
                .last("LIMIT 1"));
        if (instance == null) {
            throw new BizException("流程实例不存在");
        }
        return instance;
    }

    /** 批量按 flow_instance_id 加载 lc_process_instance，避免待办/已办 N+1 查询 */
    private Map<String, LcProcessInstance> loadInstancesByFlowIds(List<String> flowInstanceIds) {
        if (CollUtil.isEmpty(flowInstanceIds)) {
            return Map.of();
        }
        return instanceMapper.selectList(new LambdaQueryWrapper<LcProcessInstance>()
                        .in(LcProcessInstance::getFlowInstanceId, flowInstanceIds))
                .stream()
                .collect(Collectors.toMap(LcProcessInstance::getFlowInstanceId, Function.identity(), (a, b) -> a));
    }

    /**
     * 更新实例状态并将当前节点置空（ currentNode 清空必须用 UpdateWrapper 显式 set null，
     * updateById 默认字段策略会跳过 null 值）
     */
    private void updateInstanceStatus(Long instanceId, String status) {
        instanceMapper.update(null, new LambdaUpdateWrapper<LcProcessInstance>()
                .eq(LcProcessInstance::getId, instanceId)
                .set(LcProcessInstance::getStatus, status)
                .set(LcProcessInstance::getCurrentNode, null));
    }

    /** Flowable API 返回 java.util.Date，统一转 LocalDateTime */
    private LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /** 新增一条 pending 任务轨迹行 */
    private void insertPendingTask(Long instanceId, Task task, String assignee) {
        LcProcessTask row = new LcProcessTask();
        row.setInstanceId(instanceId);
        row.setTaskId(task.getId());
        row.setNodeName(task.getName());
        row.setAssignee(assignee);
        row.setResult(RESULT_PENDING);
        taskMapper.insert(row);
    }

    /** 将该 Flowable 任务的 pending 轨迹行置为终态（approved/rejected/transferred） */
    private void completeTaskRow(String taskId, String result, String comment) {
        taskMapper.update(null, new LambdaUpdateWrapper<LcProcessTask>()
                .eq(LcProcessTask::getTaskId, taskId)
                .eq(LcProcessTask::getResult, RESULT_PENDING)
                .set(LcProcessTask::getResult, result)
                .set(LcProcessTask::getComment, comment)
                .set(LcProcessTask::getHandleTime, LocalDateTime.now()));
    }
}
