package com.gencode.lowcode.db.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DDL 预览出参（POST /lc/db/ddl/preview → data = { ddl }）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DdlPreviewVO {

    /** 生成的 CREATE TABLE 语句（含注释语句，多语句以分号+换行分隔） */
    private String ddl;
}
