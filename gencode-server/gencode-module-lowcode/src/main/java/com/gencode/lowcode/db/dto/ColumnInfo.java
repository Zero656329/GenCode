package com.gencode.lowcode.db.dto;

import lombok.Data;

/**
 * 表列信息（GET /lc/db/columns 逆向读表结构）
 */
@Data
public class ColumnInfo {

    /** 列名 */
    private String columnName;

    /** 数据库原始类型名（如 VARCHAR/BIGINT/NUMBER） */
    private String typeName;

    /** 列注释 */
    private String comment;

    /** 是否可空 */
    private boolean nullable;

    /** 是否主键 */
    private boolean pk;
}
