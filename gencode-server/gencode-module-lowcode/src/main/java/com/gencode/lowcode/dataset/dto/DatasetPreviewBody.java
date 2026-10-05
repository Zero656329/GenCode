package com.gencode.lowcode.dataset.dto;

import lombok.Data;

import java.util.Map;

/**
 * 数据集预览/取数请求体（body { params: {...} }，键为 SQL 中 #{name} 的参数名）
 */
@Data
public class DatasetPreviewBody {

    /** 参数名 -> 参数值（缺省绑定 null） */
    private Map<String, Object> params;
}
