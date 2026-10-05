package com.gencode.system.dict.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.system.dict.dto.DictTypeBody;
import com.gencode.system.dict.dto.DictTypeQuery;
import com.gencode.system.dict.entity.SysDictType;
import com.gencode.system.dict.service.DictTypeService;
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
 * 字典类型管理
 */
@RestController
@RequestMapping("/system/dict/type")
@RequiredArgsConstructor
public class DictTypeController {

    private final DictTypeService dictTypeService;

    @GetMapping("/page")
    public R<PageResult<SysDictType>> page(DictTypeQuery query) {
        return R.ok(dictTypeService.page(query));
    }

    /** 全量启用类型（下拉用） */
    @GetMapping("/all")
    public R<List<SysDictType>> listAll() {
        return R.ok(dictTypeService.listAll());
    }

    @OperLog(module = "字典管理", businessType = "INSERT")
    @SaCheckPermission("system:dict:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody DictTypeBody body) {
        dictTypeService.create(body);
        return R.ok();
    }

    @OperLog(module = "字典管理", businessType = "UPDATE")
    @SaCheckPermission("system:dict:edit")
    @PutMapping
    public R<Void> update(@Valid @RequestBody DictTypeBody body) {
        dictTypeService.update(body);
        return R.ok();
    }

    /** 删除类型并级联删除数据 */
    @OperLog(module = "字典管理", businessType = "DELETE")
    @SaCheckPermission("system:dict:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        dictTypeService.delete(id);
        return R.ok();
    }
}
