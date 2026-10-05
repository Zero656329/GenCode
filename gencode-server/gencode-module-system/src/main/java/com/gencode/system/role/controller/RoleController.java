package com.gencode.system.role.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.system.role.dto.RoleBody;
import com.gencode.system.role.dto.RoleMenuBody;
import com.gencode.system.role.dto.RoleQuery;
import com.gencode.system.role.dto.RoleVO;
import com.gencode.system.role.entity.SysRole;
import com.gencode.system.role.service.RoleService;
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

import java.util.List;

/**
 * 角色管理
 */
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/page")
    public R<PageResult<SysRole>> page(RoleQuery query) {
        return R.ok(roleService.page(query));
    }

    /** 全量列表（下拉用） */
    @GetMapping("/list/all")
    public R<List<SysRole>> listAll() {
        return R.ok(roleService.listAll());
    }

    /** 详情（含 menuIds） */
    @GetMapping("/{id}")
    public R<RoleVO> detail(@PathVariable Long id) {
        return R.ok(roleService.detail(id));
    }

    @OperLog(module = "角色管理", businessType = "INSERT")
    @SaCheckPermission("system:role:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody RoleBody body) {
        roleService.create(body);
        return R.ok();
    }

    @OperLog(module = "角色管理", businessType = "UPDATE")
    @SaCheckPermission("system:role:edit")
    @PutMapping
    public R<Void> update(@Valid @RequestBody RoleBody body) {
        roleService.update(body);
        return R.ok();
    }

    @OperLog(module = "角色管理", businessType = "DELETE")
    @SaCheckPermission("system:role:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }

    /** 保存角色菜单 */
    @OperLog(module = "角色管理", businessType = "UPDATE")
    @SaCheckPermission("system:role:edit")
    @PutMapping("/{id}/menus")
    public R<Void> assignMenus(@PathVariable Long id, @RequestBody RoleMenuBody body) {
        roleService.assignMenus(id, body.getMenuIds());
        return R.ok();
    }

    /** 启用/停用 */
    @OperLog(module = "角色管理", businessType = "UPDATE")
    @SaCheckPermission("system:role:edit")
    @PutMapping("/{id}/status/{status}")
    public R<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        roleService.updateStatus(id, status);
        return R.ok();
    }
}
