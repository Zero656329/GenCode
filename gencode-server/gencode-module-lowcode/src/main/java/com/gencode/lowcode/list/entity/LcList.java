package com.gencode.lowcode.list.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 低代码列表定义
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_list")
public class LcList extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    /** 列表编码（唯一，不可修改） */
    private String code;
    /** 列表名称 */
    private String name;
    /** 数据源类型：TABLE 单表 / SQL 自定义 / API 接口（预留） */
    private String sourceType;
    /** 数据源配置 JSON：TABLE{datasourceId,tableName,pkField} SQL{datasourceId,sql} */
    private String sourceConfig;
    /** 列表 Schema JSON：columns/search/buttons */
    private String listSchema;
    /** 状态：0草稿 1已发布 2停用 */
    private Integer status;
    /** 发布版本号 */
    private Integer version;
    /** 已发布快照 JSON：{sourceConfig, listSchema} */
    private String publishedSchema;
    /** 最近发布时间 */
    private LocalDateTime publishTime;
    private String remark;
}
