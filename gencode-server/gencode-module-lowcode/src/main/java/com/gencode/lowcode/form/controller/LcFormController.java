package com.gencode.lowcode.form.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.lowcode.form.dto.FormCreateBody;
import com.gencode.lowcode.form.dto.FormPublishedVO;
import com.gencode.lowcode.form.dto.FormQuery;
import com.gencode.lowcode.form.dto.FormSaveBody;
import com.gencode.lowcode.form.entity.LcForm;
import com.gencode.lowcode.form.service.LcFormService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单定义管理（/api/lc/form）
 */
@RestController
@RequestMapping("/lc/form")
@RequiredArgsConstructor
public class LcFormController {

    private final LcFormService formService;

    /** 分页（keyword 匹配 code/name） */
    @SaCheckPermission("lc:form:list")
    @GetMapping("/page")
    public R<PageResult<LcForm>> page(FormQuery query) {
        return R.ok(formService.page(query));
    }

    /** 详情（含 schemaJson） */
    @SaCheckPermission("lc:form:list")
    @GetMapping("/{id}")
    public R<LcForm> detail(@PathVariable Long id) {
        return R.ok(formService.detail(id));
    }

    /** 新建草稿（schemaJson 置空，version=0，status=0） */
    @OperLog(module = "表单设计", businessType = "INSERT")
    @SaCheckPermission("lc:form:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody FormCreateBody body) {
        formService.create(body);
        return R.ok();
    }

    /** 保存设计（code 不可修改） */
    @OperLog(module = "表单设计", businessType = "UPDATE")
    @SaCheckPermission("lc:form:edit")
    @PutMapping
    public R<Void> saveDesign(@Valid @RequestBody FormSaveBody body) {
        formService.saveDesign(body);
        return R.ok();
    }

    /** 逻辑删除（已发布表单需先停用） */
    @OperLog(module = "表单设计", businessType = "DELETE")
    @SaCheckPermission("lc:form:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        formService.delete(id);
        return R.ok();
    }

    /** 发布：version+1，publishedSchema=当前 schemaJson，status=1 */
    @OperLog(module = "表单设计", businessType = "PUBLISH")
    @SaCheckPermission("lc:form:publish")
    @PutMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        formService.publish(id);
        return R.ok();
    }

    /** 修改状态（0草稿 1已发布 2停用，主要用于停用已发布表单） */
    @OperLog(module = "表单设计", businessType = "UPDATE")
    @SaCheckPermission("lc:form:edit")
    @PutMapping("/{id}/status/{status}")
    public R<Void> changeStatus(@PathVariable Long id, @PathVariable Integer status) {
        formService.changeStatus(id, status);
        return R.ok();
    }

    /** 运行时接口：按编码取已发布表单（仅需登录，供填报页渲染） */
    @GetMapping("/publish/{code}")
    public R<FormPublishedVO> getPublished(@PathVariable String code) {
        return R.ok(formService.getPublished(code));
    }
}
