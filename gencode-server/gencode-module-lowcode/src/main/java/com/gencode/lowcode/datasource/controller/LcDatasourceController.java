package com.gencode.lowcode.datasource.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.lowcode.datasource.dto.DatasourceCreateBody;
import com.gencode.lowcode.datasource.dto.DatasourceOptionVO;
import com.gencode.lowcode.datasource.dto.DatasourceQuery;
import com.gencode.lowcode.datasource.dto.DatasourceTestResult;
import com.gencode.lowcode.datasource.dto.DatasourceUpdateBody;
import com.gencode.lowcode.datasource.entity.LcDatasource;
import com.gencode.lowcode.datasource.service.LcDatasourceService;
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
 * 外部数据源管理（/api/lc/datasource）
 */
@RestController
@RequestMapping("/lc/datasource")
@RequiredArgsConstructor
public class LcDatasourceController {

    private final LcDatasourceService datasourceService;

    /** 分页（keyword 匹配 name；password 永远不回显） */
    @SaCheckPermission("lc:datasource:list")
    @GetMapping("/page")
    public R<PageResult<LcDatasource>> page(DatasourceQuery query) {
        return R.ok(datasourceService.page(query));
    }

    /** 下拉选项（仅 id/name） */
    @SaCheckPermission("lc:datasource:list")
    @GetMapping("/list/all")
    public R<List<DatasourceOptionVO>> listAll() {
        return R.ok(datasourceService.listAll());
    }

    /** 新建（password AES 加密落库） */
    @OperLog(module = "数据源管理", businessType = "INSERT")
    @SaCheckPermission("lc:datasource:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody DatasourceCreateBody body) {
        datasourceService.create(body);
        return R.ok();
    }

    /** 修改（password 不传 = 不修改） */
    @OperLog(module = "数据源管理", businessType = "UPDATE")
    @SaCheckPermission("lc:datasource:edit")
    @PutMapping
    public R<Void> update(@Valid @RequestBody DatasourceUpdateBody body) {
        datasourceService.update(body);
        return R.ok();
    }

    /** 逻辑删除 */
    @OperLog(module = "数据源管理", businessType = "DELETE")
    @SaCheckPermission("lc:datasource:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        datasourceService.delete(id);
        return R.ok();
    }

    /** 连接测试（失败返回 ok=false + 异常摘要，不抛 500） */
    @OperLog(module = "数据源管理")
    @SaCheckPermission("lc:datasource:edit")
    @PostMapping("/{id}/test")
    public R<DatasourceTestResult> test(@PathVariable Long id) {
        return R.ok(datasourceService.test(id));
    }
}
