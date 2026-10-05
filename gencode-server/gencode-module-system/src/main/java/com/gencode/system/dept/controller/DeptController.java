package com.gencode.system.dept.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.system.dept.dto.DeptBody;
import com.gencode.system.dept.dto.DeptTreeNode;
import com.gencode.system.dept.service.DeptService;
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

import java.util.List;

/**
 * 部门管理
 */
@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    /** 完整部门树 */
    @GetMapping("/tree")
    public R<List<DeptTreeNode>> tree(@RequestParam(required = false) String name,
                                      @RequestParam(required = false) Integer status) {
        return R.ok(deptService.tree(name, status));
    }

    @OperLog(module = "部门管理", businessType = "INSERT")
    @SaCheckPermission("system:dept:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody DeptBody body) {
        deptService.create(body);
        return R.ok();
    }

    @OperLog(module = "部门管理", businessType = "UPDATE")
    @SaCheckPermission("system:dept:edit")
    @PutMapping
    public R<Void> update(@Valid @RequestBody DeptBody body) {
        deptService.update(body);
        return R.ok();
    }

    @OperLog(module = "部门管理", businessType = "DELETE")
    @SaCheckPermission("system:dept:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return R.ok();
    }
}
