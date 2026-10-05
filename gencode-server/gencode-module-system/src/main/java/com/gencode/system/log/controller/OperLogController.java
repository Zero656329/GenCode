package com.gencode.system.log.controller;

import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.system.log.dto.OperLogQuery;
import com.gencode.system.log.entity.SysOperLog;
import com.gencode.system.log.service.OperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志
 */
@RestController
@RequestMapping("/system/log/oper")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    @GetMapping("/page")
    public R<PageResult<SysOperLog>> page(OperLogQuery query) {
        return R.ok(operLogService.page(query));
    }
}
