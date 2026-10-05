package com.gencode.lowcode.list.dto;

import lombok.Data;

/**
 * 已发布列表 VO（运行时接口出参：列表运行页按 code 渲染）
 */
@Data
public class ListPublishedVO {

    /** 列表编码 */
    private String code;
    /** 列表名称 */
    private String name;
    /** 发布版本号 */
    private Integer version;
    /** 数据源类型：TABLE | SQL | API */
    private String sourceType;
    /** 数据源配置（发布快照中的 sourceConfig） */
    private String sourceConfig;
    /** 列表 Schema（发布快照中的 listSchema） */
    private String listSchema;
}
