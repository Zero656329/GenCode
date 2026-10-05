package com.gencode.system.auth.controller;

import com.gencode.common.result.R;
import com.gencode.system.auth.dto.CaptchaVO;
import com.gencode.system.auth.dto.LoginBody;
import com.gencode.system.auth.dto.LoginResult;
import com.gencode.system.auth.dto.LoginUser;
import com.gencode.system.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 图形验证码 */
    @GetMapping("/captcha")
    public R<CaptchaVO> captcha() {
        return R.ok(authService.captcha());
    }

    /** 登录 */
    @PostMapping("/login")
    public R<LoginResult> login(@Valid @RequestBody LoginBody body) {
        return R.ok(authService.login(body));
    }

    /** 当前登录用户 */
    @GetMapping("/me")
    public R<LoginUser> me() {
        return R.ok(authService.me());
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }
}
