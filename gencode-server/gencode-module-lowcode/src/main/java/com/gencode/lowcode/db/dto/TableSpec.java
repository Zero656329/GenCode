package com.gencode.lowcode.db.dto;

import lombok.Data;

import java.util.List;

/**
 * 建表规格（可视化建表入参）：表名 + 注释 + 用户列清单
 */
@Data
public class TableSpec {

    /** 表名（标识符白名单 ^[A-Za-z_][A-Za-z0-9_]*$） */
    private String tableName;

    /** 表注释 */
    private String tableComment;

    /** 用户列（统一约定列由服务端自动追加，无需传入） */
    private List<ColumnSpec> columns;
}
