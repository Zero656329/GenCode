package com.gencode.lowcode.dataset.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.lowcode.dataset.dto.DatasetCreateBody;
import com.gencode.lowcode.dataset.dto.DatasetPreviewBody;
import com.gencode.lowcode.dataset.dto.DatasetPublishedVO;
import com.gencode.lowcode.dataset.dto.DatasetQuery;
import com.gencode.lowcode.dataset.dto.DatasetSaveBody;
import com.gencode.lowcode.dataset.entity.LcDataset;
import com.gencode.lowcode.dataset.service.DatasetService;
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

import java.util.Map;

/**
 * 数据集管理（/api/lc/dataset）
 */
@RestController
@RequestMapping("/lc/dataset")
@RequiredArgsConstructor
public class LcDatasetController {

    private final DatasetService datasetService;

    /** 分页（keyword 匹配 code/name） */
    @SaCheckPermission("lc:report:list")
    @GetMapping("/page")
    public R<PageResult<LcDataset>> page(DatasetQuery query) {
        return R.ok(datasetService.page(query));
    }

    /** 详情（含 sqlText/paramsJson） */
    @SaCheckPermission("lc:report:list")
    @GetMapping("/{id}")
    public R<LcDataset> detail(@PathVariable Long id) {
        return R.ok(datasetService.detail(id));
    }

    /** 新建（仅 SELECT，同列表 SQL 安全规则） */
    @OperLog(module = "数据集", businessType = "INSERT")
    @SaCheckPermission("lc:report:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody DatasetCreateBody body) {
        datasetService.create(body);
        return R.ok();
    }

    /** 保存（code 不可修改） */
    @OperLog(module = "数据集", businessType = "UPDATE")
    @SaCheckPermission("lc:report:edit")
    @PutMapping
    public R<Void> saveDesign(@Valid @RequestBody DatasetSaveBody body) {
        datasetService.saveDesign(body);
        return R.ok();
    }

    /** 逻辑删除 */
    @OperLog(module = "数据集", businessType = "DELETE")
    @SaCheckPermission("lc:report:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        datasetService.delete(id);
        return R.ok();
    }

    /** 预览取数（限 100 行；仅需登录，供报表设计器预览） */
    @PostMapping("/{id}/preview")
    public R<DatasetPublishedVO> preview(@PathVariable Long id,
                                         @RequestBody(required = false) DatasetPreviewBody body) {
        Map<String, Object> params = body == null || body.getParams() == null ? Map.of() : body.getParams();
        return R.ok(datasetService.preview(id, params));
    }

    /** 图表取数（params 为 URL 编码 JSON；仅需登录，供大屏/图表渲染） */
    @GetMapping("/publish/{code}/data")
    public R<DatasetPublishedVO> publishedData(@PathVariable String code,
                                               @RequestParam(required = false) String params) {
        return R.ok(datasetService.publishedData(code, parseParams(params)));
    }

    /** params= URL 编码 JSON 解析为 Map（解析失败报"params 格式错误"） */
    private Map<String, Object> parseParams(String params) {
        if (StrUtil.isBlank(params)) {
            return Map.of();
        }
        try {
            return JSONUtil.parseObj(params);
        } catch (Exception e) {
            throw new BizException("params 格式错误");
        }
    }
}
