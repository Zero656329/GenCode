package com.gencode.lowcode.form.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 低代码表单定义
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_form")
public class LcForm extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    /** 表单编码（唯一，不可修改） */
    private String code;
    /** 表单名称 */
    private String name;
    /** 设计 Schema JSON */
    private String schemaJson;
    /** 状态：0草稿 1已发布 2停用 */
    private Integer status;
    /** 发布版本号 */
    private Integer version;
    /** 已发布 Schema 快照 */
    private String publishedSchema;
    /** 最近发布时间 */
    private LocalDateTime publishTime;
    private String remark;
}
