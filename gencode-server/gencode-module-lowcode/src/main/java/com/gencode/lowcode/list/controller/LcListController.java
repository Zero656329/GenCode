package com.gencode.lowcode.list.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.lowcode.list.dto.ColumnVO;
import com.gencode.lowcode.list.dto.ListCreateBody;
import com.gencode.lowcode.list.dto.ListPublishedVO;
import com.gencode.lowcode.list.dto.ListQuery;
import com.gencode.lowcode.list.dto.ListSaveBody;
import com.gencode.lowcode.list.entity.LcList;
import com.gencode.lowcode.list.service.LcListService;
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
 * 列表定义管理（/api/lc/list）
 */
@RestController
@RequestMapping("/lc/list")
@RequiredArgsConstructor
public class LcListController {

    private final LcListService listService;

    /** 分页（keyword 匹配 code/name） */
    @SaCheckPermission("lc:list:list")
    @GetMapping("/page")
    public R<PageResult<LcList>> page(ListQuery query) {
        return R.ok(listService.page(query));
    }

    /** 详情（含 sourceConfig/listSchema JSON 字符串） */
    @SaCheckPermission("lc:list:list")
    @GetMapping("/{id}")
    public R<LcList> detail(@PathVariable Long id) {
        return R.ok(listService.detail(id));
    }

    /** 反读表列（datasourceId 空=平台主库） */
    @SaCheckPermission("lc:list:edit")
    @GetMapping("/columns")
    public R<List<ColumnVO>> columns(@RequestParam(required = false) Long datasourceId,
                                     @RequestParam String tableName) {
        return R.ok(listService.columns(datasourceId, tableName));
    }

    /** 新建（version=0，status=0） */
    @OperLog(module = "列表设计", businessType = "INSERT")
    @SaCheckPermission("lc:list:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody ListCreateBody body) {
        listService.create(body);
        return R.ok();
    }

    /** 保存（code 不可修改） */
    @OperLog(module = "列表设计", businessType = "UPDATE")
    @SaCheckPermission("lc:list:edit")
    @PutMapping
    public R<Void> save(@Valid @RequestBody ListSaveBody body) {
        listService.save(body);
        return R.ok();
    }

    /** 逻辑删除（已发布列表需先停用） */
    @OperLog(module = "列表设计", businessType = "DELETE")
    @SaCheckPermission("lc:list:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        listService.delete(id);
        return R.ok();
    }

    /** 发布：version+1，published_schema=JSON{sourceConfig,listSchema} 快照，status=1 */
    @OperLog(module = "列表设计", businessType = "PUBLISH")
    @SaCheckPermission("lc:list:publish")
    @PutMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        listService.publish(id);
        return R.ok();
    }

    /** 修改状态（0草稿 1已发布 2停用，主要用于停用已发布列表） */
    @OperLog(module = "列表设计", businessType = "UPDATE")
    @SaCheckPermission("lc:list:edit")
    @PutMapping("/{id}/status/{status}")
    public R<Void> changeStatus(@PathVariable Long id, @PathVariable Integer status) {
        listService.changeStatus(id, status);
        return R.ok();
    }

    /** 运行时接口：按编码取已发布列表（仅需登录，供列表运行页渲染） */
    @GetMapping("/publish/{code}")
    public R<ListPublishedVO> getPublished(@PathVariable String code) {
        return R.ok(listService.getPublished(code));
    }
}
