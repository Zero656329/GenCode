package com.gencode.system.user.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.system.user.dto.ResetPwdBody;
import com.gencode.system.user.dto.UserBody;
import com.gencode.system.user.dto.UserQuery;
import com.gencode.system.user.dto.UserVO;
import com.gencode.system.user.service.UserService;
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
 * 用户管理
 */
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 分页（keyword 匹配 username/nickname/phone；deptId 含子部门） */
    @GetMapping("/page")
    public R<PageResult<UserVO>> page(UserQuery query) {
        return R.ok(userService.page(query));
    }

    /** 详情（含 roleIds） */
    @GetMapping("/{id}")
    public R<UserVO> detail(@PathVariable Long id) {
        return R.ok(userService.detail(id));
    }

    @OperLog(module = "用户管理", businessType = "INSERT")
    @SaCheckPermission("system:user:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody UserBody body) {
        userService.create(body);
        return R.ok();
    }

    @OperLog(module = "用户管理", businessType = "UPDATE")
    @SaCheckPermission("system:user:edit")
    @PutMapping
    public R<Void> update(@Valid @RequestBody UserBody body) {
        userService.update(body);
        return R.ok();
    }

    @OperLog(module = "用户管理", businessType = "DELETE")
    @SaCheckPermission("system:user:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }

    /** 启用/停用 */
    @OperLog(module = "用户管理", businessType = "UPDATE")
    @SaCheckPermission("system:user:edit")
    @PutMapping("/{id}/status/{status}")
    public R<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        userService.updateStatus(id, status);
        return R.ok();
    }

    /** 重置密码 */
    @OperLog(module = "用户管理", businessType = "UPDATE")
    @SaCheckPermission("system:user:resetPwd")
    @PutMapping("/{id}/password")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPwdBody body) {
        userService.resetPassword(id, body.getPassword());
        return R.ok();
    }
}
