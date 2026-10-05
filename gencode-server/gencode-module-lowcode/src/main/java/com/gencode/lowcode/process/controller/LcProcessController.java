package com.gencode.lowcode.process.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.lowcode.process.dto.ProcessCommentBody;
import com.gencode.lowcode.process.dto.ProcessCreateBody;
import com.gencode.lowcode.process.dto.ProcessPublishedVO;
import com.gencode.lowcode.process.dto.ProcessQuery;
import com.gencode.lowcode.process.dto.ProcessSaveBody;
import com.gencode.lowcode.process.dto.ProcessStartBody;
import com.gencode.lowcode.process.dto.ProcessTaskVO;
import com.gencode.lowcode.process.dto.ProcessTraceVO;
import com.gencode.lowcode.process.dto.ProcessTransferBody;
import com.gencode.lowcode.process.entity.LcProcessDef;
import com.gencode.lowcode.process.entity.LcProcessInstance;
import com.gencode.lowcode.process.service.ProcessDefService;
import com.gencode.lowcode.process.service.ProcessRuntimeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程定义 + 流程运行（/api/lc/process）
 */
@RestController
@RequestMapping("/lc/process")
@RequiredArgsConstructor
public class LcProcessController {

    private final ProcessDefService defService;
    private final ProcessRuntimeService runtimeService;

    // ---------------------------------------------------------------- 流程定义

    /** 分页（keyword 匹配 code/name/category） */
    @SaCheckPermission("lc:process:list")
    @GetMapping("/page")
    public R<PageResult<LcProcessDef>> page(ProcessQuery query) {
        return R.ok(defService.page(query));
    }

    /** 详情（含 bpmnXml） */
    @SaCheckPermission("lc:process:list")
    @GetMapping("/{id}")
    public R<LcProcessDef> detail(@PathVariable Long id) {
        return R.ok(defService.detail(id));
    }

    /** 新建草稿（publishVersion=0，status=0） */
    @OperLog(module = "流程设计", businessType = "INSERT")
    @SaCheckPermission("lc:process:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody ProcessCreateBody body) {
        defService.create(body);
        return R.ok();
    }

    /** 保存（code 不可修改） */
    @OperLog(module = "流程设计", businessType = "UPDATE")
    @SaCheckPermission("lc:process:edit")
    @PutMapping
    public R<Void> save(@Valid @RequestBody ProcessSaveBody body) {
        defService.save(body);
        return R.ok();
    }

    /** 逻辑删除 */
    @OperLog(module = "流程设计", businessType = "DELETE")
    @SaCheckPermission("lc:process:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        defService.delete(id);
        return R.ok();
    }

    /** 部署到 Flowable：bpmnXml 必填，flow_key 取 BPMN process id，publish_version+1，status=1 */
    @OperLog(module = "流程设计", businessType = "PUBLISH")
    @SaCheckPermission("lc:process:deploy")
    @PutMapping("/{id}/deploy")
    public R<Void> deploy(@PathVariable Long id) {
        defService.deploy(id);
        return R.ok();
    }

    /** 运行时接口：按编码取已部署定义（仅需登录，供发起页使用） */
    @GetMapping("/publish/{code}")
    public R<ProcessPublishedVO> getPublished(@PathVariable String code) {
        return R.ok(defService.getPublished(code));
    }

    // ---------------------------------------------------------------- 流程运行

    /** 发起流程：写 lc_process_instance + 首任务 pending，返回实例ID */
    @OperLog(module = "流程运行", businessType = "INSERT")
    @SaCheckPermission("lc:process:start")
    @PostMapping("/{code}/start")
    public R<Long> start(@PathVariable String code, @Valid @RequestBody ProcessStartBody body) {
        return R.ok(runtimeService.start(code, body));
    }

    /** 我发起的实例分页（含当前节点/状态） */
    @SaCheckPermission("lc:process:mine")
    @GetMapping("/mine/instances")
    public R<PageResult<LcProcessInstance>> mineInstances(@RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "1") Integer pageNum,
                                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(runtimeService.mineInstances(status, pageNum, pageSize));
    }

    /** 我的待办（Flowable assignee=当前人） */
    @SaCheckPermission("lc:process:mine")
    @GetMapping("/todo")
    public R<PageResult<ProcessTaskVO>> todo(@RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(runtimeService.todo(pageNum, pageSize));
    }

    /** 我的已办（HistoryService finished 任务） */
    @SaCheckPermission("lc:process:mine")
    @GetMapping("/done")
    public R<PageResult<ProcessTaskVO>> done(@RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(runtimeService.done(pageNum, pageSize));
    }

    /** 实例详情 + 审批轨迹（登录即可） */
    @GetMapping("/instance/{id}")
    public R<ProcessTraceVO> trace(@PathVariable Long id) {
        return R.ok(runtimeService.trace(id));
    }

    /** 通过：流转到下一节点，流程结束置 instance.status=approved */
    @OperLog(module = "流程运行", businessType = "UPDATE")
    @SaCheckPermission("lc:process:approve")
    @PostMapping("/task/{taskId}/approve")
    public R<Void> approve(@PathVariable String taskId, @RequestBody(required = false) ProcessCommentBody body) {
        runtimeService.approve(taskId, body);
        return R.ok();
    }

    /** 驳回：终止流程，instance.status=rejected */
    @OperLog(module = "流程运行", businessType = "UPDATE")
    @SaCheckPermission("lc:process:approve")
    @PostMapping("/task/{taskId}/reject")
    public R<Void> reject(@PathVariable String taskId, @RequestBody(required = false) ProcessCommentBody body) {
        runtimeService.reject(taskId, body);
        return R.ok();
    }

    /** 转办：改 Flowable assignee + 记录 transferred */
    @OperLog(module = "流程运行", businessType = "UPDATE")
    @SaCheckPermission("lc:process:approve")
    @PostMapping("/task/{taskId}/transfer")
    public R<Void> transfer(@PathVariable String taskId, @Valid @RequestBody ProcessTransferBody body) {
        runtimeService.transfer(taskId, body);
        return R.ok();
    }

    /** 撤回：仅发起人且审批中可撤，deleteProcessInstance 并 status=withdrawn */
    @OperLog(module = "流程运行", businessType = "UPDATE")
    @SaCheckPermission("lc:process:mine")
    @PostMapping("/instance/{id}/withdraw")
    public R<Void> withdraw(@PathVariable Long id) {
        runtimeService.withdraw(id);
        return R.ok();
    }
}
