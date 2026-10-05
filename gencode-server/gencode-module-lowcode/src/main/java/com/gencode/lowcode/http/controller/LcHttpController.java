package com.gencode.lowcode.http.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.lowcode.http.dto.HttpApiCreateBody;
import com.gencode.lowcode.http.dto.HttpApiQuery;
import com.gencode.lowcode.http.dto.HttpApiSaveBody;
import com.gencode.lowcode.http.dto.HttpCallBody;
import com.gencode.lowcode.http.dto.HttpCallResultVO;
import com.gencode.lowcode.http.dto.HttpLogQuery;
import com.gencode.lowcode.http.entity.LcHttpApi;
import com.gencode.lowcode.http.entity.LcHttpLog;
import com.gencode.lowcode.http.service.LcHttpApiService;
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
 * 第三方 HTTP 接口管理（/api/lc/http）
 */
@RestController
@RequestMapping("/lc/http")
@RequiredArgsConstructor
public class LcHttpController {

    private final LcHttpApiService httpApiService;

    /** 分页（keyword 匹配 code/name） */
    @SaCheckPermission("lc:http:list")
    @GetMapping("/page")
    public R<PageResult<LcHttpApi>> page(HttpApiQuery query) {
        return R.ok(httpApiService.page(query));
    }

    /** 详情 */
    @SaCheckPermission("lc:http:list")
    @GetMapping("/{id}")
    public R<LcHttpApi> detail(@PathVariable Long id) {
        return R.ok(httpApiService.detail(id));
    }

    /** 新建（status=0 启用） */
    @OperLog(module = "接口管理", businessType = "INSERT")
    @SaCheckPermission("lc:http:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody HttpApiCreateBody body) {
        httpApiService.create(body);
        return R.ok();
    }

    /** 保存（code 不可修改） */
    @OperLog(module = "接口管理", businessType = "UPDATE")
    @SaCheckPermission("lc:http:edit")
    @PutMapping
    public R<Void> update(@Valid @RequestBody HttpApiSaveBody body) {
        httpApiService.update(body);
        return R.ok();
    }

    /** 逻辑删除 */
    @OperLog(module = "接口管理", businessType = "DELETE")
    @SaCheckPermission("lc:http:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        httpApiService.delete(id);
        return R.ok();
    }

    /** 调用第三方接口（调用日志落 lc_http_log，不再进操作日志） */
    @SaCheckPermission("lc:http:call")
    @PostMapping("/{code}/call")
    public R<HttpCallResultVO> call(@PathVariable String code, @RequestBody(required = false) HttpCallBody body) {
        return R.ok(httpApiService.call(code, body));
    }

    /** 调用日志分页（apiCode 精确过滤，createTime 倒序） */
    @SaCheckPermission("lc:http:list")
    @GetMapping("/log/page")
    public R<PageResult<LcHttpLog>> pageLog(HttpLogQuery query) {
        return R.ok(httpApiService.pageLog(query));
    }
}
