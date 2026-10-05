package com.gencode.lowcode.list.controller;

import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.lowcode.list.dto.ListDataDeleteBody;
import com.gencode.lowcode.list.dto.ListDataQuery;
import com.gencode.lowcode.list.dto.ListDataSaveBody;
import com.gencode.lowcode.list.engine.DynamicQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 列表运行时数据（/api/lc/list/data，仅需登录，无权限注解）
 */
@RestController
@RequestMapping("/lc/list/data")
@RequiredArgsConstructor
public class ListDataController {

    private final DynamicQueryService dynamicQueryService;

    /** 列表数据分页查询（仅已发布列表） */
    @PostMapping("/{code}")
    public R<PageResult<Map<String, Object>>> query(@PathVariable String code,
                                                    @RequestBody(required = false) ListDataQuery body) {
        return R.ok(dynamicQueryService.query(code, body));
    }

    /** 行保存（仅 TABLE 型，列按 list_schema.columns 白名单过滤） */
    @OperLog(module = "列表运行", businessType = "UPDATE")
    @PostMapping("/save/{code}")
    public R<Void> save(@PathVariable String code, @Valid @RequestBody ListDataSaveBody body) {
        dynamicQueryService.save(code, body);
        return R.ok();
    }

    /** 行删除（仅 TABLE 型，按 pk 删） */
    @OperLog(module = "列表运行", businessType = "DELETE")
    @PostMapping("/delete/{code}")
    public R<Void> delete(@PathVariable String code, @Valid @RequestBody ListDataDeleteBody body) {
        dynamicQueryService.delete(code, body);
        return R.ok();
    }
}
