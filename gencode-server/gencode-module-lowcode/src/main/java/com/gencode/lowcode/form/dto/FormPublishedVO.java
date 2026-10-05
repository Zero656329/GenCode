package com.gencode.lowcode.form.dto;

import lombok.Data;

/**
 * 已发布表单 VO（运行时接口出参：表单填报页按 code 渲染）
 */
@Data
public class FormPublishedVO {

    /** 表单编码 */
    private String code;
    /** 表单名称 */
    private String name;
    /** 发布版本号 */
    private Integer version;
    /** 已发布 Schema 快照（对外仍叫 schemaJson） */
    private String schemaJson;
}
