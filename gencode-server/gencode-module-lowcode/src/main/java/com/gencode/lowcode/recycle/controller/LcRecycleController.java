package com.gencode.lowcode.recycle.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.lowcode.recycle.RecycleService;
import com.gencode.lowcode.recycle.dto.RecycleItemVO;
import com.gencode.lowcode.recycle.dto.RecycleRestoreBody;
import com.gencode.lowcode.recycle.dto.RecycleTypeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据回收站（/api/lc/recycle）
 */
@RestController
@RequestMapping("/lc/recycle")
@RequiredArgsConstructor
public class LcRecycleController {

    private final RecycleService recycleService;

    /** 支持的回收站类型 */
    @SaCheckPermission("lc:recycle:list")
    @GetMapping("/types")
    public R<List<RecycleTypeVO>> types() {
        return R.ok(recycleService.types());
    }

    /** 已删除行分页（keyword 匹配 name/code） */
    @SaCheckPermission("lc:recycle:list")
    @GetMapping("/page")
    public R<PageResult<RecycleItemVO>> page(@RequestParam String type,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                             @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return R.ok(recycleService.page(type, keyword, pageNum, pageSize));
    }

    /** 恢复：deleted 置 0 */
    @OperLog(module = "回收站", businessType = "UPDATE")
    @SaCheckPermission("lc:recycle:restore")
    @PostMapping("/restore")
    public R<Void> restore(@Valid @RequestBody RecycleRestoreBody body) {
        recycleService.restore(body.getType(), body.getId());
        return R.ok();
    }

    /** 彻底删除：物理 DELETE */
    @OperLog(module = "回收站", businessType = "DELETE")
    @SaCheckPermission("lc:recycle:restore")
    @PostMapping("/purge")
    public R<Void> purge(@Valid @RequestBody RecycleRestoreBody body) {
        recycleService.purge(body.getType(), body.getId());
        return R.ok();
    }
}
