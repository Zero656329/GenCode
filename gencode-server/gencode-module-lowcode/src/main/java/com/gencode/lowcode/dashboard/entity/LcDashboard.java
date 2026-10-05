package com.gencode.lowcode.dashboard.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 低代码数据大屏定义
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_dashboard")
public class LcDashboard extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    /** 大屏编码（唯一，不可修改） */
    private String code;
    /** 大屏名称 */
    private String name;
    /** 布局 JSON {items:[{type,chartType,datasetCode,title,xField,yField,seriesField,x,y,w,h,refreshSec}]} */
    private String layoutJson;
    /** 状态：0草稿 1已发布 2停用 */
    private Integer status;
    /** 发布版本号 */
    private Integer version;
    /** 已发布布局快照 */
    private String publishedSchema;
    /** 最近发布时间 */
    private LocalDateTime publishTime;
    private String remark;
}
