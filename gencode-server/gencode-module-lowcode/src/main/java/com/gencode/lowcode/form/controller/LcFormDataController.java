package com.gencode.lowcode.form.controller;

import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.lowcode.form.dto.FormDataQuery;
import com.gencode.lowcode.form.dto.FormDataSubmitBody;
import com.gencode.lowcode.form.entity.LcFormData;
import com.gencode.lowcode.form.service.LcFormDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单填报数据（/api/lc/form/data，仅需登录，供运行时填报页使用）
 */
@RestController
@RequestMapping("/lc/form/data")
@RequiredArgsConstructor
public class LcFormDataController {

    private final LcFormDataService formDataService;

    /** 填报提交（表单须已发布） */
    @OperLog(module = "表单填报", businessType = "INSERT")
    @PostMapping("/{code}")
    public R<Void> submit(@PathVariable String code, @Valid @RequestBody FormDataSubmitBody body) {
        formDataService.submit(code, body);
        return R.ok();
    }

    /** 填报数据分页（formCode 精确过滤） */
    @GetMapping("/page")
    public R<PageResult<LcFormData>> page(FormDataQuery query) {
        return R.ok(formDataService.page(query));
    }
}
