package com.gencode.lowcode.list.dto;

import lombok.Data;

/**
 * 表列反读 VO
 */
@Data
public class ColumnVO {

    /** 列名 */
    private String columnName;
    /** 类型名（如 VARCHAR/BIGINT） */
    private String typeName;
    /** 列注释 */
    private String comment;
}
