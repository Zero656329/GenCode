package com.gencode.lowcode.db.dto;

import lombok.Data;

/**
 * 建表列规格（可视化建表入参，POST /lc/db/ddl/preview|execute）
 */
@Data
public class ColumnSpec {

    /** 列名（标识符白名单 ^[A-Za-z_][A-Za-z0-9_]*$） */
    private String name;

    /** 逻辑类型：varchar/int/bigint/datetime/text/decimal（tinyint 为约定列 deleted 内部类型） */
    private String typeName;

    /** 长度：varchar 默认 255；decimal 作为精度 p 默认 10 */
    private Integer length;

    /** 小数位：decimal 用，默认 2 */
    private Integer scale;

    /** 列注释 */
    private String comment;

    /** 是否非空 */
    private boolean notNull;

    /**
     * 业务主键标记：平台约定主键恒为自动追加的 id（雪花 BIGINT），
     * 勾选的列将生成 UNIQUE 约束而非替换主键
     */
    private boolean pk;

    /** 默认值（数值类型不加引号，其余按字符串字面量） */
    private String defaultValue;
}
