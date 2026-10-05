package com.gencode.system.log.controller;

import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.system.log.dto.LoginLogQuery;
import com.gencode.system.log.entity.SysLoginLog;
import com.gencode.system.log.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志
 */
@RestController
@RequestMapping("/system/log/login")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    @GetMapping("/page")
    public R<PageResult<SysLoginLog>> page(LoginLogQuery query) {
        return R.ok(loginLogService.page(query));
    }
}
