package com.gencode.lowcode.gen.dto;

import lombok.Data;

/**
 * 可生成表清单项（GET /lc/gen/tables）
 */
@Data
public class GenTableVO {

    /** 表名 */
    private String tableName;

    /** 表注释 */
    private String tableComment;
}
