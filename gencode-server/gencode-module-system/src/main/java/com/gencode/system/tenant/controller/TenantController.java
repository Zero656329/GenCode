package com.gencode.system.tenant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.system.tenant.dto.TenantBody;
import com.gencode.system.tenant.dto.TenantQuery;
import com.gencode.system.tenant.entity.SysTenant;
import com.gencode.system.tenant.service.TenantService;
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
 * 租户管理
 */
@RestController
@RequestMapping("/system/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping("/page")
    public R<PageResult<SysTenant>> page(TenantQuery query) {
        return R.ok(tenantService.page(query));
    }

    @OperLog(module = "租户管理", businessType = "INSERT")
    @SaCheckPermission("system:tenant:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody TenantBody body) {
        tenantService.create(body);
        return R.ok();
    }

    @OperLog(module = "租户管理", businessType = "UPDATE")
    @SaCheckPermission("system:tenant:edit")
    @PutMapping
    public R<Void> update(@Valid @RequestBody TenantBody body) {
        tenantService.update(body);
        return R.ok();
    }

    @OperLog(module = "租户管理", businessType = "DELETE")
    @SaCheckPermission("system:tenant:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return R.ok();
    }

    /** 启用/停用 */
    @OperLog(module = "租户管理", businessType = "UPDATE")
    @SaCheckPermission("system:tenant:edit")
    @PutMapping("/{id}/status/{status}")
    public R<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        tenantService.updateStatus(id, status);
        return R.ok();
    }
}
