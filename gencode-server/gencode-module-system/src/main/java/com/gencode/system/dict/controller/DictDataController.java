package com.gencode.system.dict.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.system.dict.dto.DictDataBody;
import com.gencode.system.dict.dto.DictDataQuery;
import com.gencode.system.dict.dto.DictDataVO;
import com.gencode.system.dict.entity.SysDictData;
import com.gencode.system.dict.service.DictDataService;
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
 * 字典数据管理
 */
@RestController
@RequestMapping("/system/dict/data")
@RequiredArgsConstructor
public class DictDataController {

    private final DictDataService dictDataService;

    /** 按类型分页 */
    @GetMapping("/page")
    public R<PageResult<SysDictData>> page(DictDataQuery query) {
        return R.ok(dictDataService.page(query));
    }

    /** 不分页取启用字典（渲染用，可缓存） */
    @GetMapping("/by-type/{dictType}")
    public R<List<DictDataVO>> listByType(@PathVariable String dictType) {
        return R.ok(dictDataService.listByType(dictType));
    }

    @OperLog(module = "字典管理", businessType = "INSERT")
    @SaCheckPermission("system:dict:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody DictDataBody body) {
        dictDataService.create(body);
        return R.ok();
    }

    @OperLog(module = "字典管理", businessType = "UPDATE")
    @SaCheckPermission("system:dict:edit")
    @PutMapping
    public R<Void> update(@Valid @RequestBody DictDataBody body) {
        dictDataService.update(body);
        return R.ok();
    }

    @OperLog(module = "字典管理", businessType = "DELETE")
    @SaCheckPermission("system:dict:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        dictDataService.delete(id);
        return R.ok();
    }
}
