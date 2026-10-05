package com.gencode.system.menu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.system.menu.dto.MenuBody;
import com.gencode.system.menu.dto.MenuTreeNode;
import com.gencode.system.menu.entity.SysMenu;
import com.gencode.system.menu.service.MenuService;
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
 * 菜单管理
 */
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /** 完整菜单树（name 模糊过滤） */
    @GetMapping("/tree")
    public R<List<MenuTreeNode>> tree(@RequestParam(required = false) String name,
                                      @RequestParam(required = false) Integer status) {
        return R.ok(menuService.tree(name, status));
    }

    @GetMapping("/{id}")
    public R<SysMenu> detail(@PathVariable Long id) {
        return R.ok(menuService.detail(id));
    }

    @OperLog(module = "菜单管理", businessType = "INSERT")
    @SaCheckPermission("system:menu:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody MenuBody body) {
        menuService.create(body);
        return R.ok();
    }

    @OperLog(module = "菜单管理", businessType = "UPDATE")
    @SaCheckPermission("system:menu:edit")
    @PutMapping
    public R<Void> update(@Valid @RequestBody MenuBody body) {
        menuService.update(body);
        return R.ok();
    }

    @OperLog(module = "菜单管理", businessType = "DELETE")
    @SaCheckPermission("system:menu:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return R.ok();
    }
}
