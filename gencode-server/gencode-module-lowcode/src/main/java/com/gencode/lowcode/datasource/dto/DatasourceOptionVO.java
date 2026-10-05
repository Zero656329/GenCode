package com.gencode.lowcode.datasource.dto;

import lombok.Data;

/**
 * 数据源下拉选项 VO（仅 id + name，不含任何敏感信息）
 */
@Data
public class DatasourceOptionVO {

    private Long id;
    private String name;
}
