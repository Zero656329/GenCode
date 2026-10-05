package com.gencode.system.config.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.system.config.dto.ConfigBody;
import com.gencode.system.config.dto.ConfigQuery;
import com.gencode.system.config.entity.SysConfig;
import com.gencode.system.config.service.ConfigService;
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
 * 参数配置管理
 */
@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping("/page")
    public R<PageResult<SysConfig>> page(ConfigQuery query) {
        return R.ok(configService.page(query));
    }

    /** 按键取值（data 直接为字符串值） */
    @GetMapping("/key/{key}")
    public R<String> getValueByKey(@PathVariable String key) {
        return R.ok(configService.getValueByKey(key));
    }

    @OperLog(module = "参数配置", businessType = "INSERT")
    @SaCheckPermission("system:config:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody ConfigBody body) {
        configService.create(body);
        return R.ok();
    }

    @OperLog(module = "参数配置", businessType = "UPDATE")
    @SaCheckPermission("system:config:edit")
    @PutMapping
    public R<Void> update(@Valid @RequestBody ConfigBody body) {
        configService.update(body);
        return R.ok();
    }

    @OperLog(module = "参数配置", businessType = "DELETE")
    @SaCheckPermission("system:config:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return R.ok();
    }
}
