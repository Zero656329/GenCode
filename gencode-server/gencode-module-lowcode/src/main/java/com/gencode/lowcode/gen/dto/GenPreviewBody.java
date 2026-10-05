package com.gencode.lowcode.gen.dto;

import lombok.Data;

/**
 * 代码生成预览/下载入参（POST /lc/gen/preview、POST /lc/gen/download）
 */
@Data
public class GenPreviewBody {

    /** 目标表名（平台主库） */
    private String tableName;

    /** 生成选项 */
    private GenOptions options;

    /**
     * 生成选项：包名/模块名/作者/业务名
     */
    @Data
    public static class GenOptions {

        /** Java 包名，默认 com.gencode.business */
        private String packageName;

        /** 模块名（后端 URL 前缀与权限前缀），默认 business */
        private String moduleName;

        /** 作者，默认 gencode */
        private String author;

        /** 业务名（URL、权限、前端目录名），默认按表名推导 */
        private String businessName;
    }
}
