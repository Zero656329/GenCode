package com.gencode.lowcode.dataset.service;

import com.gencode.common.result.PageResult;
import com.gencode.lowcode.dataset.dto.DatasetCreateBody;
import com.gencode.lowcode.dataset.dto.DatasetPublishedVO;
import com.gencode.lowcode.dataset.dto.DatasetQuery;
import com.gencode.lowcode.dataset.dto.DatasetSaveBody;
import com.gencode.lowcode.dataset.entity.LcDataset;

import java.util.Map;

/**
 * 数据集定义服务（参数化 SELECT 取数）
 */
public interface DatasetService {

    /** 分页查询（keyword 匹配 code/name） */
    PageResult<LcDataset> page(DatasetQuery query);

    /** 详情（含 sqlText/paramsJson） */
    LcDataset detail(Long id);

    /** 新建（sqlText 跑 SQL 安全校验） */
    void create(DatasetCreateBody body);

    /** 保存（code 不可修改，sqlText 跑 SQL 安全校验） */
    void saveDesign(DatasetSaveBody body);

    /** 逻辑删除 */
    void delete(Long id);

    /** 预览取数：安全层校验 + #{name} 转参数绑定，限 100 行 */
    DatasetPublishedVO preview(Long id, Map<String, Object> params);

    /** 图表取数：按当前定义直接执行（数据集无发布概念），同 preview 限 100 行 */
    DatasetPublishedVO publishedData(String code, Map<String, Object> params);
}
