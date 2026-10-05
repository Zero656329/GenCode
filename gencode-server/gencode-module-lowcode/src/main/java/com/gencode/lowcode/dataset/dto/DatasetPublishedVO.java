package com.gencode.lowcode.dataset.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 数据集取数结果 VO（preview / publish/{code}/data 出参：列名 + 行对象，行数上限 100）
 */
@Data
public class DatasetPublishedVO {

    /** 字段名数组（取自结果集元数据） */
    private List<String> columns;
    /** 行对象列表（每行 字段名 -> 值），最多 100 行 */
    private List<Map<String, Object>> rows;
}
